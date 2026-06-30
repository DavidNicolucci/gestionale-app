package com.gestionale.dominio.auth.controller;

import com.gestionale.dominio.auth.model.LoginRequest;
import com.gestionale.dominio.auth.model.LoginResponse;
import com.gestionale.dominio.auth.service.AuthService;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.annotation.security.PermitAll;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    @PermitAll                              // il login DEVE essere accessibile senza autenticazione
    public LoginResponse login(@Valid LoginRequest req) {
        String token = authService.autentica(req.username, req.password);
        return new LoginResponse(token, req.username);
    }
}
