package com.gestionale.dominio.imports;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.opentelemetry.api.trace.Span;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Path("/api/import")
@Tag(name = "Import", description = "Caricamento massivo delle ore da file")
public class ImportResource {

    private static final Logger LOG = Logger.getLogger(ImportResource.class);

    @ConfigProperty(name = "import.upload-dir")
    String uploadDir;

    @Inject CodaImport coda;
    @Inject ImportJobService jobService;

    // L'identita' viene da qui e non dal body: e' il cookie firmato, che il client non
    // puo' falsificare. Finisce su import_job.caricato_da, cosi' chi guarda un import
    // fallito sa a chi chiedere del file.
    @Inject JsonWebToken jwt;

    @POST
    @Path("/timesheet")
    @Consumes(MediaType.MULTIPART_FORM_DATA)   // qui arriva un file, non del JSON
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Carica un file di ore da importare",
            description = "Riceve un file con le ore dei dipendenti, lo salva e lo mette in coda per l'elaborazione. "
                    + "La risposta e' immediata (202: file ricevuto) e non aspetta la fine dell'import: le righe "
                    + "vengono inserite poco dopo, in background. Il file va inviato come form-data nel campo \"file\". "
                    + "La risposta contiene l'id dell'import, con cui un ADMIN puo' seguirne l'esito su "
                    + "/api/admin/import/{id}. "
                    + "Se lo stesso identico file e' gia' stato caricato risponde 409 senza rielaborarlo: "
                    + "per rifarlo comunque si passa forza=true.")
    public Response upload(@RestForm("file") FileUpload file,
                           @RestForm("forza") Boolean forza) throws IOException {

        // 1. Crea la cartella di upload se non esiste
        java.nio.file.Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);

        // 2. Salva il file con un nome unico, cosi' due upload con lo stesso nome
        //    non si sovrascrivono a vicenda
        String nomeUnivoco = UUID.randomUUID() + "_" + file.fileName();
        java.nio.file.Path destinazione = dir.resolve(nomeUnivoco);
        Files.copy(file.uploadedFile(), destinazione, StandardCopyOption.REPLACE_EXISTING);

        // 3. L'impronta del contenuto: e' quella che riconosce il file gia' caricato.
        //    Si calcola dopo la copia, sul file fermo su disco, e non sullo stream in
        //    arrivo: lo stream lo si puo' leggere una volta sola, e serve per la copia.
        String impronta = ImprontaFile.sha256(destinazione);

        // 4. Lo stesso file, due volte.
        //    Non e' un caso di scuola: l'upload risponde 202 e non dice mai com'e'
        //    finita, quindi chi non era sicuro ricarica. Senza questo controllo le ore
        //    verrebbero contate due volte, in silenzio, su numeri che finiscono in
        //    fattura. Il vincolo uq_timesheet_giorno lo impedirebbe comunque riga per
        //    riga, ma qui lo fermiamo prima, dicendo all'utente cos'e' successo invece
        //    di far girare a vuoto un import.
        if (!Boolean.TRUE.equals(forza)) {
            Optional<ImportJob> gia = jobService.giaPreso(impronta);
            if (gia.isPresent()) {
                Files.deleteIfExists(destinazione);   // la copia appena fatta non serve a nessuno
                return giaCaricato(gia.get(), file.fileName());
            }
        }

        // 5. La riga che seguira' questo file fino alla fine. Nasce prima del messaggio:
        //    se il broker fosse irraggiungibile resterebbe un ACCODATO che non parte
        //    mai, e comparirebbe nell'elenco degli import da guardare. Al contrario,
        //    accodare prima di registrare vorrebbe dire un messaggio che cita un job
        //    inesistente.
        ImportJob job = jobService.registraUpload(
                file.fileName(), destinazione.toString(), impronta, jwt.getName());

        // 6. Mette in coda solo il percorso del file, non il file intero
        coda.accoda(job);

        Span.current().setAttribute("import.file_name", file.fileName());
        Span.current().setAttribute("import.job_id", job.id);

        // 7. Risponde subito senza aspettare: 202 vuol dire "ricevuto, ci lavoro dopo"
        return Response.accepted()
                .entity(new EsitoUpload(job.id, "File ricevuto, elaborazione in corso"))
                .build();
    }

    private Response giaCaricato(ImportJob gia, String nomeFile) {
        LOG.infof("Upload di %s ignorato: stesso contenuto dell'import %d (%s)",
                nomeFile, gia.id, gia.stato);
        return Response.status(Response.Status.CONFLICT)
                .entity(new EsitoUpload(gia.id,
                        "Questo file e' gia' stato caricato (import " + gia.id + ", stato "
                                + gia.stato + " del " + gia.creatoIl + "). Le ore non sono state"
                                + " reinserite. Per caricarlo comunque, ripetere con forza=true"))
                .build();
    }

    /** La risposta dell'upload: l'id serve a seguire l'import, il messaggio a leggerlo. */
    public static class EsitoUpload {
        public Long importId;
        public String messaggio;

        public EsitoUpload(Long importId, String messaggio) {
            this.importId = importId;
            this.messaggio = messaggio;
        }
    }
}
