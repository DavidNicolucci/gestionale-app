package com.gestionale.dominio.resources;

import com.gestionale.dominio.model.dto.TimesheetRequest;
import com.gestionale.dominio.model.dto.TimesheetResponse;
import com.gestionale.dominio.service.TimesheetService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/timesheet")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TimesheetResource {

    @Inject
    TimesheetService service;

    @GET
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public List<TimesheetResponse> lista() {
        return service.listaTutti().stream().map(TimesheetResponse::da).toList();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public TimesheetResponse dettaglio(@PathParam("id") Long id) {
        return TimesheetResponse.da(service.trovaPerId(id));
    }

    @POST
    @RolesAllowed({"ADMIN", "OPERATOR"})    // le ore le inserisce anche l'OPERATOR
    public TimesheetResponse crea(@Valid TimesheetRequest req) {
        return TimesheetResponse.da(service.crea(req));
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public TimesheetResponse aggiorna(@PathParam("id") Long id, @Valid TimesheetRequest req) {
        return TimesheetResponse.da(service.aggiorna(id, req));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")                   // ma cancella solo l'ADMIN
    public void elimina(@PathParam("id") Long id) {
        service.elimina(id);
    }
}