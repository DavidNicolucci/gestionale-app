package com.gestionale.dominio.auth.model;

import java.util.Set;

/**
 * Esito del login: il token da mettere nel cookie e i ruoli dell'utente.
 *
 * I ruoli servono al frontend per non mostrare comandi che poi si prenderebbero
 * un 403 (per esempio l'assistente AI, riservato ad ADMIN e OPERATOR). Restano
 * comunque un'informazione di comodo: chi decide davvero e' @RolesAllowed sul
 * backend, che legge i ruoli dal token firmato.
 */
public record Autenticazione(String token, Set<String> ruoli) {
}
