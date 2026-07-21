package com.gestionale.dominio.resources;

import com.gestionale.dominio.model.dto.DipendentePatchRequest;
import com.gestionale.dominio.model.dto.DipendenteRequest;
import com.gestionale.dominio.model.dto.DipendenteResponse;
import com.gestionale.dominio.model.dto.DipendenteRicercaRequest;
import com.gestionale.dominio.model.dto.PaginaResponse;
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
    DipendenteService service;

    @GET
    @RolesAllowed({"ADMIN", "OPERATOR"})       // leggono entrambi i ruoli
    public List<DipendenteResponse> lista() {
        return service.listaTutti();
    }

    // Tabella paginata della home. E' un POST perche' i filtri stanno nel body:
    // sono troppi per metterli nell'indirizzo.
    @POST
    @Path("/ricerca")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public PaginaResponse<DipendenteResponse> ricerca(DipendenteRicercaRequest req) {
        return service.cerca(req != null ? req : new DipendenteRicercaRequest());
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public DipendenteResponse dettaglio(@PathParam("id") Long id) {
        return service.trovaPerId(id);
    }

    @POST
    @RolesAllowed("ADMIN")                      // crea solo l'ADMIN
    public DipendenteResponse crea(@Valid DipendenteRequest req) {
        return service.crea(req);
    }

    // PATCH e non PUT: si mandano solo i campi da cambiare, gli altri restano come sono.
    @PATCH
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public DipendenteResponse aggiorna(@PathParam("id") Long id, @Valid DipendentePatchRequest req) {
        return service.aggiorna(id, req);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")                      // cancella solo l'ADMIN
    public void elimina(@PathParam("id") Long id) {
        service.elimina(id);
    }
}
