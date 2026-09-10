package com.gestionale.dominio.auth.controller;

import com.gestionale.dominio.ai.ConversazioneService;
import com.gestionale.dominio.auth.model.Autenticazione;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Autenticazione", description = "Entrare e uscire dal gestionale e sapere chi e' l'utente collegato")
public class AuthResource {

    // Nome del cookie che contiene il token. Deve essere uguale a mp.jwt.token.cookie
    // scritto in application.properties, altrimenti Quarkus non lo trova.
    public static final String COOKIE_NAME = "gestionale_jwt";
    // Stessa durata del token (8 ore, vedi AuthService)
    private static final int COOKIE_MAX_AGE_SECONDS = 8 * 60 * 60;

    @Inject
    AuthService authService;

    @Inject
    ConversazioneService conversazione;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/login")
    @PermitAll                              // ovvio: al login si arriva senza essere loggati
    @Operation(
            summary = "Accedi con username e password",
            description = "Controlla le credenziali e, se sono giuste, apre la sessione: il token di accesso viene "
                    + "messo in un cookie sicuro che il browser rimanda da solo a ogni chiamata successiva, "
                    + "mentre nella risposta arrivano username e ruoli. La sessione dura 8 ore. "
                    + "Credenziali sbagliate: 401.")
    public Response login(@Valid LoginRequest req) {
        Autenticazione esito = authService.autentica(req.username, req.password);
        String token = esito.token();
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
        return Response.ok(new LoginResponse(req.username, esito.ruoli()))
                .cookie(cookie)
                .build();
    }

    @POST
    @Path("/logout")
    @PermitAll
    @Operation(
            summary = "Esci dal gestionale",
            description = "Chiude la sessione cancellando il cookie di accesso e, insieme, lo storico della chat "
                    + "con l'assistente. Si puo' chiamare anche se la sessione e' gia' scaduta: in quel caso "
                    + "non fa nulla e risponde comunque 204.")
    public Response logout() {
        // La conversazione con l'assistente muore con la sessione: storico su
        // database e memoria del modello se ne vanno insieme al cookie.
        // getName() e' null se si arriva qui senza un token valido (cookie gia'
        // scaduto, doppio click su "Esci"): in quel caso non c'e' niente da pulire.
        if (jwt.getName() != null) {
            conversazione.cancella(jwt.getName());
        }

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
    @Operation(
            summary = "Chi e' l'utente collegato",
            description = "Restituisce username e ruoli di chi sta usando l'applicazione. Il frontend la chiama "
                    + "quando si ricarica la pagina, per capire se la sessione e' ancora valida e cosa mostrare. "
                    + "Se la sessione e' scaduta risponde 401.")
    public LoginResponse me() {
        // Il frontend non puo' leggere il cookie, quindi quando si ricarica la pagina
        // chiama qui per sapere se e' ancora loggato e chi e' l'utente.
        // I ruoli arrivano dai gruppi del token: nessuna query in piu' al database.
        return new LoginResponse(jwt.getName(), jwt.getGroups());
    }
}
