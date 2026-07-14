package com.gestionale.dominio.handler;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Rete di sicurezza: intercetta TUTTO cio' che non e' gestito da un mapper piu' specifico.
 *
 * Copre sia le unchecked (NullPointerException, IllegalArgumentException...) sia le
 * checked (IOException dall'upload, JsonProcessingException dal consumer): a livello
 * JAX-RS la distinzione non esiste piu', qualunque Throwable esca da una risorsa
 * finisce qui.
 *
 * REGOLA JAX-RS: quando piu' mapper sono applicabili vince SEMPRE il piu' specifico.
 * Questo <Throwable> quindi non "ruba" le eccezioni gestite dagli altri mapper, ne'
 * quelle di sicurezza (io.quarkus.security.UnauthorizedException / ForbiddenException),
 * per cui Quarkus registra i propri mapper -> i 401 e i 403 restano intatti.
 */
@Provider
public class ThrowableMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(ThrowableMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {

        String path = uriInfo != null ? uriInfo.getPath() : null;

        // --- Caso 1: vincolo violato sul DATABASE (es. UNIQUE sul codice fiscale) ---
        // Non e' un guasto: e' il DB che rifiuta un dato incoerente, quindi 409 e non 500.
        // La eccezione arriva incapsulata (PersistenceException -> RollbackException -> ...),
        // percio' risaliamo la catena delle cause invece di mappare una classe sola.
        if (contieneVincoloDbViolato(exception)) {
            LOG.warnf("Vincolo di integrità violato su %s: %s", path, exception.getMessage());
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse(
                            Response.Status.CONFLICT.getStatusCode(),
                            "Conflict",
                            "Operazione rifiutata: viola un vincolo di integrità dei dati",
                            path))
                    .build();
        }

        // --- Caso 2: bug o guasto vero -> 500 ---
        // Il traceId lega la risposta data all'utente allo stack trace nei log:
        // l'utente segnala "errore a1b2c3", noi cerchiamo quella stringa e troviamo il caso.
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        LOG.errorf(exception, "[%s] Errore non gestito su %s", traceId, path);

        // Al client NON diciamo cosa e' andato storto: il messaggio di un'eccezione puo'
        // contenere query SQL, percorsi del filesystem, nomi di tabelle. Sono informazioni
        // che aiutano solo un attaccante. Il dettaglio resta nei log, associato al traceId.
        ErrorResponse body = new ErrorResponse(
                Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                "Internal Server Error",
                "Si è verificato un errore imprevisto",
                path);
        body.traceId = traceId;

        return Response.serverError().entity(body).build();
    }

    /** Cerca in tutta la catena delle cause una violazione di vincolo del database. */
    private boolean contieneVincoloDbViolato(Throwable e) {
        while (e != null) {
            if (e instanceof org.hibernate.exception.ConstraintViolationException) {
                return true;
            }
            if (e == e.getCause()) {       // catena auto-referenziante: evita il ciclo infinito
                break;
            }
            e = e.getCause();
        }
        return false;
    }
}
