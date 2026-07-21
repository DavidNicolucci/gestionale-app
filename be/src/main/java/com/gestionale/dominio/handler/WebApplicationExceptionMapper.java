package com.gestionale.dominio.handler;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Gestisce gli errori che lanciamo apposta per dire al client cosa ha sbagliato:
 *   - 404 dai service ("Dipendente 7 non trovato")
 *   - 401 dal login ("Credenziali non valide")
 *   - 409 quando il codice fiscale esiste gia'
 *
 * Senza questa classe il codice di stato arriverebbe giusto ma con il body vuoto,
 * e il messaggio che abbiamo scritto non lo vedrebbe nessuno.
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

        // Se chi ha lanciato l'errore aveva gia' preparato una risposta, teniamo quella.
        if (originale.hasEntity()) {
            return originale;
        }

        String path = uriInfo != null ? uriInfo.getPath() : null;

        // Errori 4xx: ha sbagliato il client, basta un warning.
        // Errori 5xx: abbiamo sbagliato noi, logghiamo tutto l'errore.
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
