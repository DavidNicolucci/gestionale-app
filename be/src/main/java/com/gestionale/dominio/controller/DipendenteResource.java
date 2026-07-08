package com.gestionale.dominio.controller;

import com.gestionale.dominio.model.dto.DipendenteRequest;
import com.gestionale.dominio.model.dto.DipendenteResponse;
import com.gestionale.dominio.service.DipendenteService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/dipendenti")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DipendenteResource {

    @Inject
    private DipendenteService service;

    @GET
    @RolesAllowed({"ADMIN", "OPERATOR"})       // entrambi i ruoli possono leggere
    public List<DipendenteResponse> lista() {
        return service.listaTutti().stream()
                .map(DipendenteResponse::da)
                .toList();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public DipendenteResponse dettaglio(@PathParam("id") Long id) {
        return DipendenteResponse.da(service.trovaPerId(id));
    }

    @POST
    @RolesAllowed("ADMIN")                      // solo ADMIN può creare
    public DipendenteResponse crea(@Valid DipendenteRequest req) {
        return DipendenteResponse.da(service.crea(req));
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public DipendenteResponse aggiorna(@PathParam("id") Long id, @Valid DipendenteRequest req) {
        return DipendenteResponse.da(service.aggiorna(id, req));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")                      // solo ADMIN può eliminare
    public void elimina(@PathParam("id") Long id) {
        service.elimina(id);
    }
}