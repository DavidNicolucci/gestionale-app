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
 * Quando i controlli di @Valid sul DTO falliscono, risponde 400 dicendo quali
 * campi sono sbagliati e perche'.
 *
 * ATTENZIONE: qui usiamo jakarta.validation.ConstraintViolationException, quella dei
 * dati in arrivo. Esiste un'altra classe con lo STESSO NOME, quella di Hibernate, che
 * riguarda invece i vincoli del database e la gestisce ThrowableMapper con un 409.
 * Occhio a non importare quella sbagliata.
 */
@Provider
public class ConstraintViolationMapper implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger LOG = Logger.getLogger(ConstraintViolationMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {

        // Costruiamo una mappa: nome del campo -> errori di quel campo.
        // Il percorso completo e' tipo "crea.req.codiceFiscale", ma al frontend serve
        // solo l'ultimo pezzo, cioe' il nome del campo.
        Map<String, List<String>> errori = exception.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        v -> {
                            String[] parti = v.getPropertyPath().toString().split("\\.");
                            return parti[parti.length - 1];
                        },
                        Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())
                ));

        // Warning e non errore: dati sbagliati in arrivo capitano di continuo, non e'
        // un guasto nostro. Se lo loggassimo come errore i log sarebbero pieni di falsi allarmi.
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
