package com.gestionale.dominio.auth.service;

import com.gestionale.dominio.security.entity.AppUser;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class AuthService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;                       // riusa l'issuer configurato in properties

    @Transactional
    public String autentica(String username, String password) {
        // 1. Cerca l'utente per username (AppUser è l'entity di sicurezza che già hai)
        AppUser user = AppUser.find("username", username).firstResult();

        // 2. Verifica esistenza + password. Messaggio generico di proposito (vedi nota sotto)
        if (user == null || !BcryptUtil.matches(password, user.password)) {
            throw new WebApplicationException("Credenziali non valide", 401);
        }

        // 3. Raccoglie i ruoli dell'utente (dalla tabella app_user_role tramite la relazione)
        Set<String> ruoli = user.roles.stream()
                .map(r -> r.roleName)
                .collect(Collectors.toSet());

        // 4. Costruisce e firma il JWT
        return Jwt.issuer(issuer)
                .upn(username)                       // "user principal name": chi è l'utente
                .groups(ruoli)                       // i ruoli -> diventano i @RolesAllowed
                .expiresIn(Duration.ofHours(8))      // scadenza del token: 8 ore
                .sign();                             // firma con la chiave privata
    }
}