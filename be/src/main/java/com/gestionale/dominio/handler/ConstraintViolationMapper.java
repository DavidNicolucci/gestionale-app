package com.gestionale.dominio.handler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduce i fallimenti di @Valid (Bean Validation) in un 400 con il dettaglio per campo.
 *
 * ATTENZIONE: questa e' jakarta.validation.ConstraintViolationException, cioe' la
 * validazione dell'INPUT (@Valid sul DTO) -> 400 Bad Request.
 * Esiste un'altra classe OMONIMA, org.hibernate.exception.ConstraintViolationException,
 * che invece rappresenta un vincolo violato sul DATABASE (es. UNIQUE sul codice fiscale)
 * -> 409 Conflict, gestita dal ThrowableMapper.
 * Stesso nome, significato opposto: e' una confusione classica.
 */
@Provider
public class ConstraintViolationMapper implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger LOG = Logger.getLogger(ConstraintViolationMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {

        // Mappa: nome del campo -> elenco dei messaggi di errore su quel campo.
        // Il propertyPath e' del tipo "crea.req.codiceFiscale": all'utente interessa
        // solo l'ultimo segmento, cioe' il nome del campo.
        Map<String, List<String>> errori = exception.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        v -> {
                            String[] parti = v.getPropertyPath().toString().split("\\.");
                            return parti[parti.length - 1];
                        },
                        Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())
                ));

        // WARN e non ERROR: un input non valido e' un evento normale, non un guasto
        // dell'applicazione. Loggarlo come errore riempirebbe i log di falsi allarmi.
        LOG.warnf("Validazione fallita su %s: %s", path(), errori);

        ErrorResponse body = new ErrorResponse(
                Response.Status.BAD_REQUEST.getStatusCode(),
                "Bad Request",
                "Dati non validi",
                path());
        body.fieldErrors = errori;

        return Response.status(Response.Status.BAD_REQUEST).entity(body).build();
    }

    private String path() {
        return uriInfo != null ? uriInfo.getPath() : null;
    }
}
