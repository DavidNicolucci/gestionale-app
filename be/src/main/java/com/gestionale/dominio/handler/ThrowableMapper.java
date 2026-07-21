package com.gestionale.dominio.handler;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Rete di sicurezza: prende tutti gli errori che nessun altro handler gestisce,
 * cosi' non esce mai una pagina di errore grezza verso il client.
 *
 * Non ruba il lavoro agli altri handler: quando ce n'e' uno piu' preciso vince quello.
 * Anche i 401 e i 403 restano gestiti da Quarkus.
 */
@Provider
public class ThrowableMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(ThrowableMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {

        String path = uriInfo != null ? uriInfo.getPath() : null;

        // Caso 1: il database ha rifiutato il dato (es. codice fiscale gia' presente).
        // Non e' un guasto nostro, quindi rispondiamo 409 e non 500.
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

        // Caso 2: errore vero, rispondiamo 500.
        // Il traceId collega la risposta all'errore nei log: l'utente ci dice
        // "errore a1b2c3" e noi cerchiamo quel codice per trovare cosa e' successo.
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        LOG.errorf(exception, "[%s] Errore non gestito su %s", traceId, path);

        // Al client non diciamo cosa e' andato storto: i messaggi di errore possono
        // contenere query e percorsi dei file, informazioni utili solo a chi ci attacca.
        // Il dettaglio resta nei log insieme al traceId.
        ErrorResponse body = new ErrorResponse(
                Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                "Internal Server Error",
                "Si è verificato un errore imprevisto",
                path);
        body.traceId = traceId;

        return Response.serverError().entity(body).build();
    }

    /**
     * L'errore del database ci arriva dentro altri errori, uno dentro l'altro,
     * quindi li scorriamo tutti fino in fondo per vedere se c'e'.
     */
    private boolean contieneVincoloDbViolato(Throwable e) {
        while (e != null) {
            if (e instanceof org.hibernate.exception.ConstraintViolationException) {
                return true;
            }
            if (e == e.getCause()) {       // se punta a se stesso, evitiamo il ciclo infinito
                break;
            }
            e = e.getCause();
        }
        return false;
    }
}
