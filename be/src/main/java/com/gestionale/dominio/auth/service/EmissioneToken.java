package com.gestionale.dominio.auth.service;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.NewCookie;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

/**
 * Costruisce il token di sessione e il cookie che lo trasporta.
 *
 * Sta in una classe sua perche' il token non si crea piu' solo al login: la sessione
 * e' "scorrevole", cioe' viene riemessa a ogni richiesta (vedi {@link com.gestionale.dominio.auth.filter.FiltroSessione}).
 * Login e rinnovo devono per forza produrre token identici nella forma, e l'unico
 * modo per esserne sicuri e' che li scriva lo stesso pezzo di codice.
 *
 * Dentro il token, oltre a chi e' l'utente e ai suoi ruoli, ci sono due numeri:
 *
 *   - token_epoch: l'"epoca" delle sessioni dell'utente, copiata dalla colonna
 *     omonima di app_user. Il confronto a ogni richiesta e' quello che permette di
 *     buttare fuori un token prima della sua scadenza (vedi {@link RevocaSessioni}).
 *
 *   - auth_time: quando l'utente ha digitato davvero la password. Il rinnovo lo
 *     ricopia sempre uguale, cosi' sopravvive ai rinnovi e ci dice da quanto dura
 *     la sessione: serve a metterle un tetto, altrimenti chi tiene una scheda aperta
 *     resterebbe dentro per sempre senza ripassare dal login.
 *
 * La scadenza del token e' la piu' vicina fra "inattivita' da adesso" e "tetto
 * massimo dal login". Mettendola nel token, il tetto lo fa rispettare la normale
 * verifica della firma: non serve ricontrollarlo a mano da nessun'altra parte.
 */
@ApplicationScoped
public class EmissioneToken {

    /**
     * Nome del cookie che contiene il token. Deve essere uguale a mp.jwt.token.cookie
     * scritto in application.properties, altrimenti Quarkus non lo trova.
     */
    public static final String COOKIE_NAME = "gestionale_jwt";

    public static final String CLAIM_TOKEN_EPOCH = "token_epoch";
    public static final String CLAIM_AUTH_TIME = "auth_time";

    private final String issuer;
    private final Duration inattivita;
    private final Duration durataMassima;
    private final Clock clock;

    @Inject
    public EmissioneToken(@ConfigProperty(name = "mp.jwt.verify.issuer") String issuer,
                          @ConfigProperty(name = "sessione.inattivita") Duration inattivita,
                          @ConfigProperty(name = "sessione.durata-massima") Duration durataMassima) {
        this(issuer, inattivita, durataMassima, Clock.systemUTC());
    }

    /** Per i test: permette di far scorrere il tempo senza aspettarlo davvero. */
    EmissioneToken(String issuer, Duration inattivita, Duration durataMassima, Clock clock) {
        this.issuer = issuer;
        this.inattivita = inattivita;
        this.durataMassima = durataMassima;
        this.clock = clock;
    }

    /**
     * Un token e la sua scadenza. Vanno insieme perche' il cookie che lo trasporta
     * deve scadere nello stesso istante: calcolare la scadenza due volte, una per il
     * token e una per il cookie, vorrebbe dire vederle divergere di un secondo ogni
     * volta che l'orologio gira fra le due chiamate.
     */
    public record Sessione(String token, Instant scadenza) {
    }

    /** Adesso, al secondo: le date dentro un JWT sono secondi, non millisecondi. */
    public Instant adesso() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * Firma un token per l'utente indicato.
     *
     * @param inizioSessione quando l'utente ha inserito la password (al login e'
     *                       adesso, al rinnovo e' l'auth_time del token precedente)
     * @return il token firmato con la sua scadenza, oppure vuoto se il tetto massimo
     *         e' gia' stato raggiunto: li' non c'e' un token da dare, si rifa' il login
     */
    public Optional<Sessione> emetti(String username, Set<String> ruoli,
                                     int tokenEpoch, Instant inizioSessione) {
        Instant scadenza = scadenza(inizioSessione);
        if (!scadenza.isAfter(adesso())) {
            return Optional.empty();
        }
        String token = Jwt.issuer(issuer)
                .upn(username)                            // chi e' l'utente
                .groups(ruoli)                            // i ruoli che poi legge @RolesAllowed
                .claim(CLAIM_TOKEN_EPOCH, tokenEpoch)     // quale "generazione" di sessioni
                .claim(CLAIM_AUTH_TIME, inizioSessione.getEpochSecond())
                .expiresAt(scadenza)
                .sign();                                  // firma con la nostra chiave privata
        return Optional.of(new Sessione(token, scadenza));
    }

    /**
     * Quando scade un token emesso adesso: fra "inattivita'" da questo momento, a meno
     * che il tetto dal login non arrivi prima. Con durata-massima a zero il tetto non
     * c'e' e conta solo l'inattivita'.
     */
    public Instant scadenza(Instant inizioSessione) {
        Instant perInattivita = adesso().plus(inattivita);
        if (durataMassima.isZero() || durataMassima.isNegative()) {
            return perInattivita;
        }
        Instant tetto = inizioSessione.plus(durataMassima);
        return tetto.isBefore(perInattivita) ? tetto : perInattivita;
    }

    /**
     * Il cookie che porta il token al browser.
     *
     * httpOnly: il JavaScript della pagina non puo' leggerlo, quindi non se lo puo'
     * rubare uno script malevolo. sameSite STRICT: il browser lo manda solo se la
     * richiesta parte dal nostro sito.
     *
     * maxAge segue la scadenza del token invece di essere fisso: quando il tetto
     * massimo si avvicina, il cookie muore insieme al token e non un minuto dopo.
     */
    public NewCookie cookie(Sessione sessione) {
        long secondi = Math.max(1, Duration.between(adesso(), sessione.scadenza()).getSeconds());
        return base()
                .value(sessione.token())
                .maxAge((int) Math.min(secondi, Integer.MAX_VALUE))
                .build();
    }

    /** Cookie vuoto con durata zero: il browser lo cancella subito. */
    public NewCookie cookieCancellato() {
        return base().value("").maxAge(0).build();
    }

    private NewCookie.Builder base() {
        return new NewCookie.Builder(COOKIE_NAME)
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite(NewCookie.SameSite.STRICT);
    }
}
