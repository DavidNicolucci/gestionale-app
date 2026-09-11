package com.gestionale.dominio.security.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * I ruoli che esistono davvero, cioe' quelli nominati dai @RolesAllowed.
 *
 * In app_user_role il ruolo e' una stringa libera: senza questo elenco un ADMIN
 * potrebbe creare un utente con ruolo "ADMINN" o "pippo", che entra ma non puo' fare
 * niente, con un 403 su ogni pagina che non si capisce da dove venga. Chi aggiunge un
 * ruolo nuovo a un @RolesAllowed lo aggiunge anche qui (RuoloTest controlla che i due
 * elenchi combacino) e nel vincolo ck_user_role_nome di 01-schema.sql, altrimenti il
 * database rifiuta l'INSERT.
 *
 * Nei @RolesAllowed restano stringhe perche' un'annotazione non puo' leggere un enum;
 * i nomi devono essere identici a quelli qui sotto.
 */
public enum Ruolo {

    /** Fa tutto, compresi cancellare e gestire gli utenti. */
    ADMIN,

    /** Lavoro quotidiano: legge, inserisce le ore, importa i timesheet, usa l'assistente. */
    OPERATOR;

    /** "ADMIN, OPERATOR": per i messaggi di errore. */
    public static final String ELENCO = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.joining(", "));

    /**
     * Il ruolo con quel nome, ignorando maiuscole e spazi ai lati: "admin" e " Admin "
     * sono ADMIN. Nel database finisce comunque sempre la forma esatta, perche' il
     * confronto dei @RolesAllowed invece e' sensibile alle maiuscole.
     */
    public static Optional<Ruolo> da(String nome) {
        if (nome == null) {
            return Optional.empty();
        }
        String cercato = nome.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(r -> r.name().equals(cercato))
                .findFirst();
    }
}
