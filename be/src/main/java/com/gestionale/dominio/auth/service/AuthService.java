package com.gestionale.dominio.auth.service;

import com.gestionale.dominio.auth.model.Autenticazione;
import com.gestionale.dominio.security.entity.AppUser;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class AuthService {

    // Password finta, usata quando l'username non esiste.
    // Serve per far durare il login sempre lo stesso tempo: se saltassimo il controllo
    // della password, la risposta arriverebbe molto piu' in fretta per un utente che non
    // esiste, e cronometrando si capirebbe quali username sono validi.
    private static final String HASH_CIVETTA =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final EmissioneToken emissione;
    private final ProtezioneLogin protezione;

    @Inject
    public AuthService(EmissioneToken emissione, ProtezioneLogin protezione) {
        this.emissione = emissione;
        this.protezione = protezione;
    }

    /**
     * @param ip da dove arriva la richiesta: serve a limitare i tentativi anche
     *           per macchina, non solo per account (vedi {@link ProtezioneLogin})
     */
    public Autenticazione autentica(String username, String password, String ip) {
        // 0. Troppi tentativi falliti su questo username o da questo IP: 429 subito,
        //    senza nemmeno guardare la password.
        try (ProtezioneLogin.Tentativo tentativo = protezione.inizia(username, ip)) {

            // 1. Cerca l'utente.
            Optional<AppUser> utente = AppUser.find("username", username).firstResultOptional();

            // 2. Controlla la password. Il controllo lo facciamo sempre, anche se l'utente
            //    non esiste (usando la password finta): deve metterci lo stesso tempo.
            String hashDaVerificare = utente.map(u -> u.password).orElse(HASH_CIVETTA);
            boolean passwordCorretta = BcryptUtil.matches(password, hashDaVerificare);

            // 3. Stesso messaggio per tutti i casi: utente inesistente, password sbagliata
            //    o account disattivato. Se li distinguessimo, diremmo a chi ci attacca
            //    quali username esistono davvero.
            Optional<AppUser> valido = utente
                    .filter(u -> passwordCorretta)
                    .filter(u -> u.enabled);
            if (valido.isEmpty()) {
                tentativo.fallito();
                throw new WebApplicationException("Credenziali non valide", 401);
            }
            AppUser user = valido.get();

            // 4. Prende i ruoli dell'utente dalla tabella app_user_role
            Set<String> ruoli = user.roles.stream()
                    .map(r -> r.roleName)
                    .collect(Collectors.toSet());

            // 5. Crea il token e lo firma. Dentro ci finisce anche user.tokenEpoch:
            //    e' la "generazione" delle sessioni di questo utente, e verra'
            //    ricontrollata a ogni richiesta per poter chiudere la sessione prima
            //    della scadenza (vedi RevocaSessioni).
            //    La sessione poi si rinnova da sola finche' l'utente lavora: il token
            //    dura sessione.inattivita e viene riemesso a ogni richiesta dal
            //    FiltroSessione, fino al tetto di sessione.durata-massima.
            Instant inizioSessione = emissione.adesso();
            EmissioneToken.Sessione sessione = emissione.emetti(username, ruoli, user.tokenEpoch, inizioSessione)
                    // Vuoto vorrebbe dire "sessione gia' finita nell'istante in cui
                    // nasce": succede solo con una configurazione senza senso
                    // (inattivita' o durata massima a zero), ed e' un errore nostro.
                    .orElseThrow(() -> new WebApplicationException(
                            "Durata della sessione configurata a zero", 500));

            // Lo diciamo solo adesso: se la firma del token fallisse, l'utente non
            // sarebbe entrato e non avrebbe senso azzerargli i fallimenti.
            tentativo.riuscito();

            // I ruoli tornano anche fuori dal token: il frontend non puo' leggere il
            // cookie, quindi senza questo non saprebbe cosa mostrare all'utente.
            return new Autenticazione(sessione.token(), sessione.scadenza(), ruoli);
        }
    }
}
