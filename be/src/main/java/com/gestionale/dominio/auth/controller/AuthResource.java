package com.gestionale.dominio.auth.controller;

import com.gestionale.dominio.ai.ConversazioneService;
import com.gestionale.dominio.auth.model.Autenticazione;
import com.gestionale.dominio.auth.model.LoginRequest;
import com.gestionale.dominio.auth.model.LoginResponse;
import com.gestionale.dominio.auth.service.AuthService;
import com.gestionale.dominio.auth.service.EmissioneToken;
import com.gestionale.dominio.auth.service.RevocaSessioni;

import io.quarkus.security.Authenticated;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
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

    @Inject
    AuthService authService;

    // Costruisce token e cookie: stessa forma al login e a ogni rinnovo.
    @Inject
    EmissioneToken emissione;

    @Inject
    RevocaSessioni revocaSessioni;

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
                    + "mentre nella risposta arrivano username e ruoli. La sessione si rinnova da sola a ogni "
                    + "chiamata: scade dopo mezz'ora di inattivita', e in ogni caso 12 ore dopo l'accesso. "
                    + "Credenziali sbagliate: 401. Troppi tentativi falliti sullo stesso username o dallo "
                    + "stesso indirizzo: 429, con l'header Retry-After che dice fra quanti secondi riprovare.")
    public Response login(@Valid LoginRequest req, @Context HttpServerRequest richiesta) {
        // L'IP di chi chiama. Dietro un reverse proxy sarebbe sempre quello del proxy:
        // in quel caso va attivato quarkus.http.proxy.proxy-address-forwarding (vedi
        // application.properties), e Vert.x mette qui l'IP vero preso da X-Forwarded-For.
        String ip = richiesta.remoteAddress() != null ? richiesta.remoteAddress().hostAddress() : null;
        Autenticazione esito = authService.autentica(req.username, req.password, ip);
        // Il token sta solo nel cookie: come e' fatto lo decide EmissioneToken, che
        // lo rifara' uguale a ogni rinnovo della sessione.
        NewCookie cookie = emissione.cookie(new EmissioneToken.Sessione(esito.token(), esito.scadenza()));
        return Response.ok(new LoginResponse(req.username, esito.ruoli()))
                .cookie(cookie)
                .build();
    }

    @POST
    @Path("/logout")
    @PermitAll
    @Operation(
            summary = "Esci dal gestionale",
            description = "Chiude la sessione e, insieme, lo storico della chat con l'assistente. Oltre a "
                    + "cancellare il cookie invalida il token sul server: una copia presa altrove smette di "
                    + "funzionare subito, invece di restare valida fino alla scadenza. Vale per tutte le "
                    + "sessioni aperte dell'utente, quindi chiude anche quelle su altri dispositivi. "
                    + "Si puo' chiamare anche se la sessione e' gia' scaduta: in quel caso non fa nulla e "
                    + "risponde comunque 204.")
    public Response logout() {
        // La conversazione con l'assistente muore con la sessione: storico su
        // database e memoria del modello se ne vanno insieme al cookie.
        // getName() e' null se si arriva qui senza un token valido (cookie gia'
        // scaduto, doppio click su "Esci"): in quel caso non c'e' niente da pulire.
        if (jwt.getName() != null) {
            conversazione.cancella(jwt.getName());
            // Cancellare il cookie non basta: il token e' firmato e resterebbe valido
            // fino alla scadenza, quindi chi ne avesse una copia potrebbe continuare a
            // usarla con curl. Incrementare l'epoca lo fa cadere davvero, adesso.
            revocaSessioni.revoca(jwt.getName(), "logout");
        }

        // Riscrive il cookie vuoto con durata 0: il browser lo cancella subito
        return Response.noContent().cookie(emissione.cookieCancellato()).build();
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
