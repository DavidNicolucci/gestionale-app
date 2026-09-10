package com.gestionale.dominio.ai;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/chat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Assistente", description = "Chat con l'assistente che risponde sui dati del gestionale")
public class ChatResource {

    @Inject
    ConversazioneService conversazione;

    // L'identita' viene da qui e non dal body: e' il cookie firmato, che il
    // client non puo' falsificare.
    @Inject
    JsonWebToken jwt;

    @POST
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Fai una domanda all'assistente",
            description = "Invia una domanda scritta in italiano e restituisce la risposta dell'assistente. "
                    + "La conversazione e' legata all'utente loggato, quindi l'assistente ricorda le domande "
                    + "precedenti fatte nella stessa sessione.")
    public ChatMessaggioResponse chat(@Valid ChatRequest richiesta) {
        return ChatMessaggioResponse.da(conversazione.rispondi(jwt.getName(), richiesta.domanda));
    }

    /** Conversazione precedente, per riempire la chat quando il frontend si riapre. */
    @GET
    @Path("/messaggi")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Leggi la conversazione gia' avvenuta",
            description = "Restituisce in ordine i messaggi scambiati finora dall'utente loggato con l'assistente. "
                    + "Serve a riempire la chat quando si riapre la pagina. Con il logout lo storico viene cancellato.")
    public List<ChatMessaggioResponse> messaggi() {
        return conversazione.storico(jwt.getName());
    }
}
