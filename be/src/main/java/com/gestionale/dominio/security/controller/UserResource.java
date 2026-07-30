package com.gestionale.dominio.security.controller;

import com.gestionale.dominio.security.model.ChangePasswordRequest;
import com.gestionale.dominio.security.model.CreateUserRequest;
import com.gestionale.dominio.security.model.UserResponse;
import com.gestionale.dominio.security.service.UserService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

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

    // Elenco di tutti gli utenti (senza password). Solo ADMIN.
    @GET
    @RolesAllowed("ADMIN")
    public List<UserResponse> lista() {
        return userService.listaUtenti();
    }

    // Cambia la password di un utente identificato dall'id nell'URL. Solo ADMIN.
    // Es. PUT /api/users/2/password  con corpo { "password": "nuovaSegreta1" }
    @PUT
    @Path("/{id}/password")
    @RolesAllowed("ADMIN")
    public Response cambiaPassword(@PathParam("id") Long id, @Valid ChangePasswordRequest req) {
        userService.cambiaPassword(id, req.password);
        // 204 No Content: operazione riuscita, niente da restituire.
        return Response.noContent().build();
    }
}
