package com.gestionale.dominio.auth.filter;

import com.gestionale.dominio.auth.service.EmissioneToken;
import com.gestionale.dominio.auth.service.RevocaSessioni;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.json.JsonNumber;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Le due cose che succedono a ogni richiesta con un token valido.
 *
 * 1. In entrata: il token e' ancora buono? La firma e la scadenza le ha gia'
 *    controllate Quarkus prima di arrivare qui, ma quelle non sanno niente di quello
 *    che e' successo dopo l'emissione. Qui si confronta l'epoca scritta nel token con
 *    quella dell'utente sul database: se non combaciano, il token e' stato revocato
 *    (logout, cambio password) e si risponde 401 anche se scadrebbe fra ore.
 *
 * 2. In uscita: il cookie viene riemesso con la scadenza spostata in avanti. E' la
 *    "sessione scorrevole": chi sta lavorando non viene buttato fuori a orologio, e
 *    la sessione muore dopo un periodo di inattivita' (sessione.inattivita) invece
 *    che a una certa ora dal login. Il tetto di sessione.durata-massima resta, ma
 *    abbastanza lontano da non cadere in mezzo alla giornata.
 *
 * Il rinnovo non avviene davvero a ogni richiesta: firmare un token costa qualche
 * millisecondo di RSA, e una pagina che ne fa venti insieme lo pagherebbe venti
 * volte per spostare la scadenza di pochi istanti. Si rifirma solo se dall'ultima
 * volta e' passato almeno sessione.rinnovo-minimo.
 *
 * Nota: la sessione scorrevole allunga anche la finestra di chi un token l'ha rubato
 * e continua a usarlo. E' il motivo per cui questo filtro fa le due cose insieme: la
 * finestra si allunga, ma adesso c'e' un modo di chiuderla di colpo (punto 1), che
 * prima non esisteva.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class FiltroSessione implements ContainerRequestFilter, ContainerResponseFilter {

    /** Dove il filtro in entrata lascia il cookie nuovo per quello in uscita. */
    private static final String COOKIE_DA_RINNOVARE = "sessione.cookie-rinnovato";

    @Inject
    JsonWebToken jwt;

    @Inject
    RevocaSessioni revocaSessioni;

    @Inject
    EmissioneToken emissione;

    @ConfigProperty(name = "sessione.rinnovo-minimo")
    Duration rinnovoMinimo;

    @Override
    public void filter(ContainerRequestContext richiesta) {
        // Nessun token: e' un endpoint aperto (login) o una richiesta che verra'
        // comunque respinta piu' avanti da @Authenticated / @RolesAllowed.
        String username = jwt.getRawToken() == null ? null : jwt.getName();
        if (username == null) {
            return;
        }

        // Un token senza l'epoca e' uno emesso prima che esistesse questo controllo:
        // non possiamo sapere se sia stato revocato, quindi non lo accettiamo. Capita
        // solo al primo avvio dopo l'aggiornamento, e si risolve rifacendo il login.
        Optional<Integer> epocaNelToken = numero(jwt.getClaim(EmissioneToken.CLAIM_TOKEN_EPOCH));
        Optional<RevocaSessioni.Stato> stato = revocaSessioni.stato(username);

        boolean valida = epocaNelToken.isPresent()
                && stato.isPresent()
                && stato.get().attivo()
                && stato.get().tokenEpoch() == epocaNelToken.get();
        if (!valida) {
            throw sessioneChiusa();
        }

        // Il token e' buono: prepariamo quello che lo sostituira'. Lo facciamo adesso
        // e non in uscita perche' qui i dati del token sono ancora a portata di mano.
        preparaRinnovo(richiesta, username, stato.get().tokenEpoch());
    }

    @Override
    public void filter(ContainerRequestContext richiesta, ContainerResponseContext risposta) {
        Object cookie = richiesta.getProperty(COOKIE_DA_RINNOVARE);
        if (cookie == null) {
            return;
        }
        // Login e logout mettono loro stessi un cookie di sessione nella risposta: il
        // rinnovo non deve sovrascriverlo, o il logout non farebbe uscire nessuno.
        if (haGiaIlCookieDiSessione(risposta)) {
            return;
        }
        risposta.getHeaders().add(HttpHeaders.SET_COOKIE, cookie);
    }

    private void preparaRinnovo(ContainerRequestContext richiesta, String username, int epoca) {
        Instant emessoIl = Instant.ofEpochSecond(jwt.getIssuedAtTime());
        if (Duration.between(emessoIl, emissione.adesso()).compareTo(rinnovoMinimo) < 0) {
            return;                       // rifirmare adesso non sposterebbe quasi niente
        }

        // auth_time e' quando l'utente ha digitato la password: si trascina uguale di
        // rinnovo in rinnovo, altrimenti il tetto massimo scorrerebbe insieme alla
        // sessione e non sarebbe piu' un tetto.
        Instant inizioSessione = numeroLungo(jwt.getClaim(EmissioneToken.CLAIM_AUTH_TIME))
                .map(Instant::ofEpochSecond)
                .orElse(emessoIl);

        Set<String> ruoli = jwt.getGroups();
        emissione.emetti(username, ruoli, epoca, inizioSessione)
                .map(emissione::cookie)
                .ifPresent(nuovo -> richiesta.setProperty(COOKIE_DA_RINNOVARE, nuovo));
        // Se il token non viene emesso siamo al tetto massimo: si lascia scadere
        // quello che c'e', cosi' la sessione finisce da sola e si ripassa dal login.
    }

    private static boolean haGiaIlCookieDiSessione(ContainerResponseContext risposta) {
        List<Object> intestazioni = risposta.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (intestazioni == null) {
            return false;
        }
        for (Object intestazione : intestazioni) {
            if (intestazione instanceof NewCookie c) {
                if (EmissioneToken.COOKIE_NAME.equals(c.getName())) {
                    return true;
                }
                continue;
            }
            if (intestazione != null
                    && String.valueOf(intestazione).startsWith(EmissioneToken.COOKIE_NAME + "=")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 401 con il cookie cancellato: il browser si porta dietro un token che non vale
     * piu', e senza questo continuerebbe a rimandarlo a ogni richiesta.
     */
    private WebApplicationException sessioneChiusa() {
        return new WebApplicationException("Sessione non piu' valida: rifare l'accesso",
                Response.status(Response.Status.UNAUTHORIZED)
                        .cookie(emissione.cookieCancellato())
                        .build());
    }

    /**
     * I claim numerici arrivano come JsonNumber o come Number a seconda di come il
     * token e' stato letto: li trattiamo allo stesso modo invece di fidarci del tipo.
     */
    private static Optional<Integer> numero(Object claim) {
        if (claim instanceof Number n) {
            return Optional.of(n.intValue());
        }
        if (claim instanceof JsonNumber n) {
            return Optional.of(n.intValue());
        }
        return Optional.empty();
    }

    /** Come {@link #numero(Object)}, ma per i secondi dall'epoca, che in un int non ci starebbero a lungo. */
    private static Optional<Long> numeroLungo(Object claim) {
        if (claim instanceof Number n) {
            return Optional.of(n.longValue());
        }
        if (claim instanceof JsonNumber n) {
            return Optional.of(n.longValue());
        }
        return Optional.empty();
    }
}
