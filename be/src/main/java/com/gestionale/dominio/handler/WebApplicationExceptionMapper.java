package com.gestionale.dominio.handler;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Gestisce le eccezioni "attese", quelle che l'applicazione lancia di proposito per
 * dire al client cosa ha sbagliato:
 *   - NotFoundException            -> 404 (dai service: "Dipendente 7 non trovato")
 *   - WebApplicationException 401  -> AuthService: "Credenziali non valide"
 *   - WebApplicationException 409  -> DipendenteService: codice fiscale duplicato
 *
 * Senza questo mapper lo status arriva giusto ma il BODY E' VUOTO: il messaggio che
 * hai scritto nell'eccezione non raggiunge mai il client.
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    private static final Logger LOG = Logger.getLogger(WebApplicationExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException exception) {

        Response originale = exception.getResponse();
        int status = originale.getStatus();

        // Se chi ha lanciato l'eccezione aveva gia' costruito un body, lo rispettiamo
        // invece di sovrascriverlo.
        if (originale.hasEntity()) {
            return originale;
        }

        String path = uriInfo != null ? uriInfo.getPath() : null;

        // 4xx = colpa del client (input sbagliato, non autorizzato): WARN, non e' un guasto.
        // 5xx = colpa nostra: ERROR, con lo stack trace.
        if (status >= 500) {
            LOG.errorf(exception, "Errore applicativo su %s", path);
        } else {
            LOG.warnf("%d su %s: %s", status, path, exception.getMessage());
        }

        ErrorResponse body = new ErrorResponse(
                status,
                Response.Status.fromStatusCode(status) != null
                        ? Response.Status.fromStatusCode(status).getReasonPhrase()
                        : "Error",
                exception.getMessage(),
                path);

        return Response.status(status).entity(body).build();
    }
}
