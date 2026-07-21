package com.gestionale.dominio.auth.controller;

import com.gestionale.dominio.auth.model.LoginRequest;
import com.gestionale.dominio.auth.model.LoginResponse;
import com.gestionale.dominio.auth.service.AuthService;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.PermitAll;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    // Nome del cookie che contiene il token. Deve essere uguale a mp.jwt.token.cookie
    // scritto in application.properties, altrimenti Quarkus non lo trova.
    public static final String COOKIE_NAME = "gestionale_jwt";
    // Stessa durata del token (8 ore, vedi AuthService)
    private static final int COOKIE_MAX_AGE_SECONDS = 8 * 60 * 60;

    @Inject
    AuthService authService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/login")
    @PermitAll                              // ovvio: al login si arriva senza essere loggati
    public Response login(@Valid LoginRequest req) {
        String token = authService.autentica(req.username, req.password);
        // Il token sta solo nel cookie. httpOnly: il JavaScript della pagina non puo'
        // leggerlo, quindi non se lo puo' rubare uno script malevolo.
        // sameSite STRICT: il browser lo manda solo se la richiesta parte dal nostro sito.
        NewCookie cookie = new NewCookie.Builder(COOKIE_NAME)
                .value(token)
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite(NewCookie.SameSite.STRICT)
                .maxAge(COOKIE_MAX_AGE_SECONDS)
                .build();
        return Response.ok(new LoginResponse(req.username))
                .cookie(cookie)
                .build();
    }

    @POST
    @Path("/logout")
    @PermitAll
    public Response logout() {
        // Riscrive il cookie vuoto con durata 0: il browser lo cancella subito
        NewCookie cookie = new NewCookie.Builder(COOKIE_NAME)
                .value("")
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite(NewCookie.SameSite.STRICT)
                .maxAge(0)
                .build();
        return Response.noContent().cookie(cookie).build();
    }

    @GET
    @Path("/me")
    @Authenticated
    public LoginResponse me() {
        // Il frontend non puo' leggere il cookie, quindi quando si ricarica la pagina
        // chiama qui per sapere se e' ancora loggato e chi e' l'utente.
        return new LoginResponse(jwt.getName());
    }
}
