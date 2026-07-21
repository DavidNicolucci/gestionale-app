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
    @Channel("import-out")                 // la coda configurata in application.properties
    Emitter<String> emitter;               // serve a mettere i messaggi in coda

    @Inject
    ObjectMapper objectMapper;             // per trasformare l'oggetto in JSON

    @POST
    @Path("/timesheet")
    @Consumes(MediaType.MULTIPART_FORM_DATA)   // qui arriva un file, non del JSON
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public Response upload(@RestForm("file") FileUpload file) throws IOException {

        // 1. Crea la cartella di upload se non esiste
        java.nio.file.Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);

        // 2. Salva il file con un nome unico, cosi' due upload con lo stesso nome
        //    non si sovrascrivono a vicenda
        String nomeUnivoco = UUID.randomUUID() + "_" + file.fileName();
        java.nio.file.Path destinazione = dir.resolve(nomeUnivoco);
        Files.copy(file.uploadedFile(), destinazione, StandardCopyOption.REPLACE_EXISTING);

        // 3. Mette in coda solo il percorso del file, non il file intero
        ImportMessage msg = new ImportMessage(destinazione.toString(), file.fileName());
        String json = objectMapper.writeValueAsString(msg);
        emitter.send(json);

        // 4. Risponde subito senza aspettare: 202 vuol dire "ricevuto, ci lavoro dopo"
        return Response.accepted()
                .entity("{\"messaggio\": \"File ricevuto, elaborazione in corso\"}")
                .build();
    }
}