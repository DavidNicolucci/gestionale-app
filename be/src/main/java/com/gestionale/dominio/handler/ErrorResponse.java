package com.gestionale.dominio.handler;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Formato unico per tutti gli errori dell'API: cosi' il frontend scrive una sola
 * funzione per gestirli, invece di indovinare il formato ogni volta.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)   // i campi vuoti non finiscono nel JSON
public class ErrorResponse {

    public Instant timestamp = Instant.now();
    public int status;                        // 400, 404, 409, 500...
    public String error;                      // etichetta breve: "Bad Request", "Conflict"
    public String message;                    // messaggio leggibile
    public String path;                       // endpoint che ha dato errore

    /** Solo per gli errori di validazione: per ogni campo, cosa non va. */
    public Map<String, List<String>> fieldErrors;

    /** Solo per i 500: il codice con cui ritrovare l'errore nei log. */
    public String traceId;

    public ErrorResponse(int status, String error, String message, String path) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }
}
