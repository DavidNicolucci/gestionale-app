package com.gestionale.dominio.resources;

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
import java.util.List;

@Path("/api/clienti")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClienteResource {
 //TODO: cambiare nome cartella con resource
    @Inject
    ClienteService service;

    @GET
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public List<ClienteResponse> lista() {
        return service.listaTutti().stream().map(ClienteResponse::da).toList();
    }

    // Tabella paginata della home. E' un POST perche' i filtri stanno nel body:
    // sono troppi per metterli nell'indirizzo. Body vuoto = prima pagina senza filtri.
    @POST
    @Path("/ricerca")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public PaginaResponse<ClienteResponse> ricerca(ClienteRicercaRequest req) {
        return service.cerca(req != null ? req : new ClienteRicercaRequest());
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public ClienteResponse dettaglio(@PathParam("id") Long id) {
        return ClienteResponse.da(service.trovaPerId(id));
    }

    @POST
    @RolesAllowed("ADMIN")
    public ClienteResponse crea(@Valid ClienteRequest req) {
        return ClienteResponse.da(service.crea(req));
    }

    //modifica patch e togliere id e passare i campi che vanno modificato solo sul dto di request
    @PUT
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public ClienteResponse aggiorna(@PathParam("id") Long id, @Valid ClienteRequest req) {
        return ClienteResponse.da(service.aggiorna(id, req));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public void elimina(@PathParam("id") Long id) {
        service.elimina(id);
    }
}