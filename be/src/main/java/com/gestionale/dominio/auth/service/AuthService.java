package com.gestionale.dominio.auth.service;

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

    @Inject
    public AuthService(@ConfigProperty(name = "mp.jwt.verify.issuer") String issuer) {
        this.issuer = issuer;
    }

    public String autentica(String username, String password) {
        // 1. Cerca l'utente.
        Optional<AppUser> utente = AppUser.find("username", username).firstResultOptional();

        // 2. Controlla la password. Il controllo lo facciamo sempre, anche se l'utente
        //    non esiste (usando la password finta): deve metterci lo stesso tempo.
        String hashDaVerificare = utente.map(u -> u.password).orElse(HASH_CIVETTA);
        boolean passwordCorretta = BcryptUtil.matches(password, hashDaVerificare);

        // 3. Stesso messaggio per tutti i casi: utente inesistente, password sbagliata
        //    o account disattivato. Se li distinguessimo, diremmo a chi ci attacca
        //    quali username esistono davvero.
        AppUser user = utente
                .filter(u -> passwordCorretta)
                .filter(u -> u.enabled)
                .orElseThrow(() -> new WebApplicationException("Credenziali non valide", 401));

        // 4. Prende i ruoli dell'utente dalla tabella app_user_role
        Set<String> ruoli = user.roles.stream()
                .map(r -> r.roleName)
                .collect(Collectors.toSet());

        // 5. Crea il token e lo firma
        return Jwt.issuer(issuer)
                .upn(username)                       // chi e' l'utente
                .groups(ruoli)                       // i ruoli che poi legge @RolesAllowed
                .expiresIn(Duration.ofHours(8))      // dopo 8 ore va rifatto il login
                .sign();                             // firma con la nostra chiave privata
    }
}
