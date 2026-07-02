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

    // Nome del cookie che trasporta il JWT: deve combaciare con mp.jwt.token.cookie in application.properties
    public static final String COOKIE_NAME = "gestionale_jwt";
    // Durata del cookie allineata alla scadenza del token (8 ore, vedi AuthService)
    private static final int COOKIE_MAX_AGE_SECONDS = 8 * 60 * 60;

    @Inject
    AuthService authService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/login")
    @PermitAll                              // il login DEVE essere accessibile senza autenticazione
    public Response login(@Valid LoginRequest req) {
        String token = authService.autentica(req.username, req.password);
        // Il token viaggia SOLO nel cookie HttpOnly: JavaScript non puo' leggerlo (protezione XSS).
        // SameSite=Strict impedisce che il browser lo invii su richieste provenienti da altri siti (protezione CSRF).
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
        // Sovrascrive il cookie con maxAge=0: il browser lo elimina subito
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
        // Il frontend non puo' leggere il cookie: chiama questo endpoint al refresh
        // della pagina per sapere se la sessione e' ancora valida e chi e' l'utente
        return new LoginResponse(jwt.getName());
    }
}
