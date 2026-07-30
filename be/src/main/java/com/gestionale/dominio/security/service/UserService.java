package com.gestionale.dominio.security.service;

import com.gestionale.dominio.security.entity.AppRole;
import com.gestionale.dominio.security.entity.AppUser;
import com.gestionale.dominio.security.model.CreateUserRequest;
import com.gestionale.dominio.security.model.UserResponse;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class UserService {

    // Ruolo assegnato quando la richiesta non ne specifica nessuno.
    private static final String RUOLO_DI_BASE = "USER";

    // @Transactional: tutte le INSERT (utente + ruoli) stanno in un'unica transazione.
    // Se qualcosa va storto a meta', si annulla tutto e non resta un utente senza ruoli.
    @Transactional
    public UserResponse creaUtente(CreateUserRequest req) {

        // 1. L'username deve essere libero. Il DB ha comunque un vincolo UNIQUE, ma
        //    controllare qui ci permette di dare un errore chiaro (409) invece di un
        //    errore grezzo di violazione di vincolo.
        boolean giaEsistente = AppUser.find("username", req.username)
                .firstResultOptional().isPresent();
        if (giaEsistente) {
            throw new WebApplicationException("Username gia' in uso", 409);
        }

        // 2. Cifra la password: nel DB va SOLO l'hash bcrypt, mai la password in chiaro.
        AppUser nuovo = new AppUser();
        nuovo.username = req.username;
        nuovo.password = BcryptUtil.bcryptHash(req.password);
        nuovo.enabled = true;
        nuovo.persist();                 // dopo il persist, nuovo.id e' valorizzato

        // 3. Decide i ruoli: quelli passati, oppure "USER" se la lista e' vuota.
        List<String> ruoli = (req.ruoli == null || req.ruoli.isEmpty())
                ? List.of(RUOLO_DI_BASE)
                : req.ruoli;

        // 4. Crea una riga in app_user_role per ogni ruolo.
        List<String> ruoliSalvati = new ArrayList<>();
        for (String nomeRuolo : ruoli) {
            AppRole ruolo = new AppRole();
            ruolo.userId = nuovo.id;
            ruolo.roleName = nomeRuolo;
            ruolo.persist();
            ruoliSalvati.add(nomeRuolo);
        }

        // 5. Restituisce i dati dell'utente creato, senza la password.
        return new UserResponse(nuovo.id, nuovo.username, nuovo.enabled, ruoliSalvati);
    }

    // Elenca tutti gli utenti con i loro ruoli. Sola lettura: niente @Transactional
    // di scrittura, ma serve comunque una transazione per leggere la collection
    // "roles" (lazy) senza incappare in LazyInitializationException.
    @Transactional
    public List<UserResponse> listaUtenti() {
        List<AppUser> utenti = AppUser.listAll();
        return utenti.stream()
                .map(u -> new UserResponse(
                        u.id,
                        u.username,
                        u.enabled,
                        u.roles.stream().map(r -> r.roleName).collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    // Imposta una nuova password per un utente esistente. Anche qui la password
    // viene cifrata (hash bcrypt): nel DB non finisce mai in chiaro.
    @Transactional
    public void cambiaPassword(Long userId, String nuovaPassword) {
        AppUser utente = AppUser.<AppUser>findByIdOptional(userId)
                .orElseThrow(() -> new WebApplicationException("Utente non trovato", 404));
        utente.password = BcryptUtil.bcryptHash(nuovaPassword);
        // Essendo un'entita' gestita dentro la transazione, la modifica viene
        // salvata in automatico al commit: non serve chiamare persist().
    }
}
