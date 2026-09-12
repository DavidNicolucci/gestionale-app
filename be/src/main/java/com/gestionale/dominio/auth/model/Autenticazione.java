package com.gestionale.dominio.auth.model;

import java.time.Instant;
import java.util.Set;

/**
 * Esito del login: il token da mettere nel cookie, quando scade e i ruoli dell'utente.
 *
 * La scadenza torna qui invece di essere ricalcolata da chi costruisce il cookie:
 * deve essere la stessa scritta dentro il token, altrimenti il cookie morirebbe
 * prima o dopo la sessione vera.
 *
 * I ruoli servono al frontend per non mostrare comandi che poi si prenderebbero
 * un 403 (per esempio l'assistente AI, riservato ad ADMIN e OPERATOR). Restano
 * comunque un'informazione di comodo: chi decide davvero e' @RolesAllowed sul
 * backend, che legge i ruoli dal token firmato.
 */
public record Autenticazione(String token, Instant scadenza, Set<String> ruoli) {
}
