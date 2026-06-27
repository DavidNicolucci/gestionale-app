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
    @Consumes(MediaType.TEXT_PLAIN)        // riceve la domanda come testo semplice
    @Produces(MediaType.TEXT_PLAIN)        // risponde con testo
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public String chat(String domanda) {
        return chatService.chat(domanda);
    }
}