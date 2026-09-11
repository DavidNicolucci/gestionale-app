package com.gestionale.dominio.auth.service;

import com.gestionale.dominio.auth.model.Autenticazione;
import com.gestionale.dominio.security.entity.AppUser;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
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

    private final String issuer;      // chi emette il token, letto dalla configurazione
    private final ProtezioneLogin protezione;

    @Inject
    public AuthService(@ConfigProperty(name = "mp.jwt.verify.issuer") String issuer,
                       ProtezioneLogin protezione) {
        this.issuer = issuer;
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

            // 5. Crea il token e lo firma
            String token = Jwt.issuer(issuer)
                    .upn(username)                       // chi e' l'utente
                    .groups(ruoli)                       // i ruoli che poi legge @RolesAllowed
                    .expiresIn(Duration.ofHours(8))      // dopo 8 ore va rifatto il login
                    .sign();                             // firma con la nostra chiave privata

            // Lo diciamo solo adesso: se la firma del token fallisse, l'utente non
            // sarebbe entrato e non avrebbe senso azzerargli i fallimenti.
            tentativo.riuscito();

            // I ruoli tornano anche fuori dal token: il frontend non puo' leggere il
            // cookie, quindi senza questo non saprebbe cosa mostrare all'utente.
            return new Autenticazione(token, ruoli);
        }
    }
}
