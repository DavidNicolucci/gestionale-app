package com.gestionale.dominio.resources;

import com.gestionale.dominio.model.dto.SitoRequest;
import com.gestionale.dominio.model.dto.SitoResponse;
import com.gestionale.dominio.service.SitoService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/siti")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SitoResource {

    @Inject
    SitoService service;

    @GET
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public List<SitoResponse> lista() {
        return service.listaTutti().stream().map(SitoResponse::da).toList();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public SitoResponse dettaglio(@PathParam("id") Long id) {
        return SitoResponse.da(service.trovaPerId(id));
    }

    @POST
    @RolesAllowed("ADMIN")
    public SitoResponse crea(@Valid SitoRequest req) {
        return SitoResponse.da(service.crea(req));
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public SitoResponse aggiorna(@PathParam("id") Long id, @Valid SitoRequest req) {
        return SitoResponse.da(service.aggiorna(id, req));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public void elimina(@PathParam("id") Long id) {
        service.elimina(id);
    }
}