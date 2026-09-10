package com.gestionale.dominio.resources;

import com.gestionale.dominio.model.dto.ClientePatchRequest;
import com.gestionale.dominio.model.dto.ClienteRequest;
import com.gestionale.dominio.model.dto.ClienteResponse;
import com.gestionale.dominio.model.dto.ClienteRicercaRequest;
import com.gestionale.dominio.model.dto.PaginaResponse;
import com.gestionale.dominio.service.ClienteService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.List;

@Path("/api/clienti")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Clienti", description = "Anagrafica dei clienti per cui si lavora")
public class ClienteResource {
 //TODO: cambiare nome cartella con resource
    @Inject
    ClienteService service;

    @GET
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Elenco di tutti i clienti",
            description = "Restituisce l'elenco completo dei clienti, senza filtri e senza pagine. "
                    + "Utile per riempire le tendine di scelta. Per la tabella con filtri usare invece /api/clienti/ricerca.")
    public List<ClienteResponse> lista() {
        return service.listaTutti().stream().map(ClienteResponse::da).toList();
    }

    // Tabella paginata della home. E' un POST perche' i filtri stanno nel body:
    // sono troppi per metterli nell'indirizzo. Body vuoto = prima pagina senza filtri.
    @POST
    @Path("/ricerca")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Cerca i clienti a pagine",
            description = "Restituisce una pagina di clienti in base ai filtri e all'ordinamento indicati nel corpo "
                    + "della richiesta. E' una POST solo perche' i filtri sono troppi per stare nell'indirizzo. "
                    + "Corpo vuoto: prima pagina, nessun filtro.")
    public PaginaResponse<ClienteResponse> ricerca(ClienteRicercaRequest req) {
        return service.cerca(req != null ? req : new ClienteRicercaRequest());
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Dettaglio di un cliente",
            description = "Restituisce tutti i dati del cliente con l'id indicato. Se l'id non esiste risponde 404.")
    public ClienteResponse dettaglio(@PathParam("id") Long id) {
        return ClienteResponse.da(service.trovaPerId(id));
    }

    @POST
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Crea un nuovo cliente",
            description = "Aggiunge un cliente all'anagrafica e restituisce i dati salvati, id compreso. "
                    + "Riservato agli ADMIN.")
    public ClienteResponse crea(@Valid ClienteRequest req) {
        return ClienteResponse.da(service.crea(req));
    }

    // PATCH e non PUT: si mandano solo i campi da cambiare, gli altri restano come sono.
    @PATCH
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Modifica un cliente",
            description = "Aggiorna solo i campi presenti nella richiesta: quelli che non vengono inviati restano "
                    + "come sono. Restituisce il cliente aggiornato. Riservato agli ADMIN.")
    public ClienteResponse aggiorna(@PathParam("id") Long id, @Valid ClientePatchRequest req) {
        return ClienteResponse.da(service.aggiorna(id, req));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Elimina un cliente",
            description = "Toglie il cliente dall'anagrafica. Attenzione: insieme al cliente vengono cancellati "
                    + "anche tutti i suoi siti. Non restituisce nulla (204). Riservato agli ADMIN.")
    public void elimina(@PathParam("id") Long id) {
        service.elimina(id);
    }
}
