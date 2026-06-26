package com.gestionale.dominio.imports;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Path("/api/import")
public class ImportResource {

    @ConfigProperty(name = "import.upload-dir")
    String uploadDir;

    @Inject
    @Channel("import-out")                 // il canale in uscita configurato in properties
    Emitter<String> emitter;               // l'Emitter PUBBLICA messaggi sul canale

    @Inject
    ObjectMapper objectMapper;             // per trasformare l'oggetto in JSON

    @POST
    @Path("/timesheet")
    @Consumes(MediaType.MULTIPART_FORM_DATA)   // riceve un file, non JSON
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public Response upload(@RestForm("file") FileUpload file) throws IOException {

        // 1. Crea la cartella di upload se non esiste
        java.nio.file.Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);

        // 2. Salva il file con un nome univoco (evita collisioni se due upload hanno lo stesso nome)
        String nomeUnivoco = UUID.randomUUID() + "_" + file.fileName();
        java.nio.file.Path destinazione = dir.resolve(nomeUnivoco);
        Files.copy(file.uploadedFile(), destinazione, StandardCopyOption.REPLACE_EXISTING);

        // 3. Costruisce il messaggio leggero e lo pubblica sulla coda come JSON
        ImportMessage msg = new ImportMessage(destinazione.toString(), file.fileName());
        String json = objectMapper.writeValueAsString(msg);
        emitter.send(json);

        // 4. Risponde SUBITO: 202 Accepted = "preso in carico, elaboro in background"
        return Response.accepted()
                .entity("{\"messaggio\": \"File ricevuto, elaborazione in corso\"}")
                .build();
    }
}