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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Utenti", description = "Gli account che possono entrare nel gestionale. Solo ADMIN")
public class UserResource {

    @Inject
    UserService userService;

    // Solo un utente loggato con ruolo ADMIN puo' creare nuovi utenti.
    // @RolesAllowed legge i ruoli dal JWT (il campo "groups" messo da AuthService):
    // chi non e' ADMIN riceve 403, chi non e' loggato 401.
    @POST
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Crea un nuovo utente",
            description = "Crea un account con username, password e ruolo (ADMIN oppure OPERATOR). La password "
                    + "viene salvata cifrata e non viene mai restituita. Risponde 201 con i dati dell'utente creato. "
                    + "Riservato agli ADMIN.")
    public Response creaUtente(@Valid CreateUserRequest req) {
        UserResponse creato = userService.creaUtente(req);
        // 201 Created: la risorsa e' stata creata; nel corpo i dati dell'utente (senza password).
        return Response.status(Response.Status.CREATED).entity(creato).build();
    }

    // Elenco di tutti gli utenti (senza password). Solo ADMIN.
    @GET
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Elenco degli utenti",
            description = "Restituisce tutti gli account con username e ruolo. Le password non vengono mai "
                    + "restituite. Riservato agli ADMIN.")
    public List<UserResponse> lista() {
        return userService.listaUtenti();
    }

    // Cambia la password di un utente identificato dall'id nell'URL. Solo ADMIN.
    // Es. PUT /api/users/2/password  con corpo { "password": "nuovaSegreta1" }
    @PUT
    @Path("/{id}/password")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Cambia la password di un utente",
            description = "Imposta una nuova password per l'utente indicato dall'id, senza chiedere quella vecchia: "
                    + "serve per reimpostare la password di chi l'ha dimenticata. Non restituisce nulla (204). "
                    + "Riservato agli ADMIN.")
    public Response cambiaPassword(@PathParam("id") Long id, @Valid ChangePasswordRequest req) {
        userService.cambiaPassword(id, req.password);
        // 204 No Content: operazione riuscita, niente da restituire.
        return Response.noContent().build();
    }
}
