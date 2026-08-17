package com.gestionale.dominio.ai;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/api/chat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatResource {

    @Inject
    ConversazioneService conversazione;

    // L'identita' viene da qui e non dal body: e' il cookie firmato, che il
    // client non puo' falsificare.
    @Inject
    JsonWebToken jwt;

    @POST
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public ChatMessaggioResponse chat(@Valid ChatRequest richiesta) {
        return ChatMessaggioResponse.da(conversazione.rispondi(jwt.getName(), richiesta.domanda));
    }

    /** Conversazione precedente, per riempire la chat quando il frontend si riapre. */
    @GET
    @Path("/messaggi")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public List<ChatMessaggioResponse> messaggi() {
        return conversazione.storico(jwt.getName());
    }
}
