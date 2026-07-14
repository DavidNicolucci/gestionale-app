package com.gestionale.dominio.handler;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Forma unica di tutte le risposte di errore dell'API.
 * Avere un solo contratto d'errore vuol dire che il frontend scrive UNA sola
 * funzione per gestirli, invece di indovinare il formato caso per caso.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)   // i campi nulli non compaiono nel JSON
public class ErrorResponse {

    public Instant timestamp = Instant.now();
    public int status;                        // 400, 404, 409, 500...
    public String error;                      // etichetta breve: "Bad Request", "Conflict"
    public String message;                    // messaggio leggibile
    public String path;                       // endpoint che ha fallito

    /** Valorizzato solo per gli errori di validazione: campo -> messaggi. */
    public Map<String, List<String>> fieldErrors;

    /** Valorizzato solo per i 500: identificativo con cui ritrovare lo stack trace nei log. */
    public String traceId;

    public ErrorResponse(int status, String error, String message, String path) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }
}
