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

    // Hash bcrypt "civetta", usato quando l'username non esiste.
    // Senza, il corto circuito dell'|| salterebbe la verifica bcrypt: la risposta
    // tornerebbe in pochi ms per un utente inesistente e in ~100ms per uno esistente
    // con password errata. Quella differenza di tempo rivela quali username sono validi
    // (timing attack -> enumerazione utenti). Facendo girare bcrypt SEMPRE, i due casi
    // costano uguale e il messaggio d'errore generico mantiene il suo scopo.
    private static final String HASH_CIVETTA =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final String issuer;      // final: letto una volta alla costruzione, mai piu' modificato

    @Inject
    public AuthService(@ConfigProperty(name = "mp.jwt.verify.issuer") String issuer) {
        this.issuer = issuer;
    }

    public String autentica(String username, String password) {
        // 1. Cerca l'utente. Optional invece di null: il "non esiste" e' nella firma.
        Optional<AppUser> utente = AppUser.find("username", username).firstResultOptional();

        // 2. Verifica la password. Il confronto gira SEMPRE, anche quando l'utente non
        //    esiste (vedi HASH_CIVETTA): il costo in tempo dev'essere lo stesso nei due casi.
        //    Da qui l'uso di map(...).orElse(...) e non di un ifPresent: serve valutare
        //    comunque il bcrypt.
        String hashDaVerificare = utente.map(u -> u.password).orElse(HASH_CIVETTA);
        boolean passwordCorretta = BcryptUtil.matches(password, hashDaVerificare);

        // 3. Un'unica risposta per tutti i motivi di fallimento: utente inesistente,
        //    password errata o account disabilitato. Distinguerli nel messaggio direbbe
        //    a un attaccante quali username esistono.
        AppUser user = utente
                .filter(u -> passwordCorretta)
                .filter(u -> u.enabled)
                .orElseThrow(() -> new WebApplicationException("Credenziali non valide", 401));

        // 4. Raccoglie i ruoli dell'utente (dalla tabella app_user_role tramite la relazione)
        Set<String> ruoli = user.roles.stream()
                .map(r -> r.roleName)
                .collect(Collectors.toSet());

        // 5. Costruisce e firma il JWT
        return Jwt.issuer(issuer)
                .upn(username)                       // "user principal name": chi è l'utente
                .groups(ruoli)                       // i ruoli -> diventano i @RolesAllowed
                .expiresIn(Duration.ofHours(8))      // scadenza del token: 8 ore
                .sign();                             // firma con la chiave privata
    }
}
