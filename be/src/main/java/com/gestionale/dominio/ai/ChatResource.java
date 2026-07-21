package com.gestionale.dominio.ai;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/chat")
public class ChatResource {

    @Inject
    ChatAiService chatService;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)        // la domanda arriva come testo, non JSON
    @Produces(MediaType.TEXT_PLAIN)        // e anche la risposta e' testo
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public String chat(String domanda) {
        return chatService.chat(domanda);
    }
}