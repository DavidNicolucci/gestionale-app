package com.gestionale.dominio.controller;

import com.gestionale.dominio.model.dto.ClienteRequest;
import com.gestionale.dominio.model.dto.ClienteResponse;
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

    @Inject
    ClienteService service;

    @GET
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public List<ClienteResponse> lista() {
        return service.listaTutti().stream().map(ClienteResponse::da).toList();
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