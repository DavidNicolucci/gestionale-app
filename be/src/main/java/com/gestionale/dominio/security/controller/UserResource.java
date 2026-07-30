package com.gestionale.dominio.security.controller;

import com.gestionale.dominio.security.model.CreateUserRequest;
import com.gestionale.dominio.security.model.UserResponse;
import com.gestionale.dominio.security.service.UserService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    UserService userService;

    // Solo un utente loggato con ruolo ADMIN puo' creare nuovi utenti.
    // @RolesAllowed legge i ruoli dal JWT (il campo "groups" messo da AuthService):
    // chi non e' ADMIN riceve 403, chi non e' loggato 401.
    @POST
    @RolesAllowed("ADMIN")
    public Response creaUtente(@Valid CreateUserRequest req) {
        UserResponse creato = userService.creaUtente(req);
        // 201 Created: la risorsa e' stata creata; nel corpo i dati dell'utente (senza password).
        return Response.status(Response.Status.CREATED).entity(creato).build();
    }
}
