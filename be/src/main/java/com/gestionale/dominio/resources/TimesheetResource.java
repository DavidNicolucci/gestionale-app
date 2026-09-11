package com.gestionale.dominio.resources;

import com.gestionale.dominio.model.dto.TimesheetPatchRequest;
import com.gestionale.dominio.model.dto.TimesheetRequest;
import com.gestionale.dominio.model.dto.TimesheetResponse;
import com.gestionale.dominio.service.TimesheetService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.List;

@Path("/api/timesheet")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Timesheet", description = "Le ore lavorate da un dipendente su un sito, giorno per giorno")
public class TimesheetResource {

    @Inject
    TimesheetService service;

    @GET
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Elenco di tutte le registrazioni di ore",
            description = "Restituisce tutte le righe di timesheet presenti, ognuna con dipendente, sito, "
                    + "data e ore lavorate.")
    public List<TimesheetResponse> lista() {
        return service.listaTutti().stream().map(TimesheetResponse::da).toList();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Dettaglio di una registrazione di ore",
            description = "Restituisce la riga di timesheet con l'id indicato. Se l'id non esiste risponde 404.")
    public TimesheetResponse dettaglio(@PathParam("id") Long id) {
        return TimesheetResponse.da(service.trovaPerId(id));
    }

    @POST
    @RolesAllowed({"ADMIN", "OPERATOR"})    // le ore le inserisce anche l'OPERATOR
    @Operation(
            summary = "Registra ore lavorate",
            description = "Inserisce una nuova riga di ore indicando dipendente, sito, data e ore. "
                    + "Se il dipendente o il sito indicati non esistono risponde 404. "
                    + "Per caricare molte righe in una volta usare invece l'import da file.")
    public TimesheetResponse crea(@Valid TimesheetRequest req) {
        return TimesheetResponse.da(service.crea(req));
    }

    // PATCH e non PUT: si mandano solo i campi da cambiare, gli altri restano come sono.
    @PATCH
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Correggi una registrazione di ore",
            description = "Aggiorna solo i campi presenti nella richiesta: quelli che non vengono inviati restano "
                    + "come sono. Serve a correggere ore, data, dipendente o sito di una riga gia' inserita.")
    public TimesheetResponse aggiorna(@PathParam("id") Long id, @Valid TimesheetPatchRequest req) {
        return TimesheetResponse.da(service.aggiorna(id, req));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")                   // ma cancella solo l'ADMIN
    @Operation(
            summary = "Elimina una registrazione di ore",
            description = "Annulla la registrazione: la riga esce da elenchi e totali ma resta sul database, "
                    + "così si può vedere che era stata inserita e rimetterla in conto. È l'unica eliminazione "
                    + "che toglie davvero delle ore dai riepiloghi, ed è il suo scopo: correggere un inserimento "
                    + "sbagliato. Ripetere la chiamata su una riga già annullata non è un errore. "
                    + "Non restituisce nulla (204). Riservato agli ADMIN.")
    public void elimina(@PathParam("id") Long id) {
        service.elimina(id);
    }

    @POST
    @Path("/{id}/ripristino")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Ripristina una registrazione annullata",
            description = "Rimette in conto una riga di ore che era stata annullata: torna negli elenchi e nei "
                    + "totali. Non ricontrolla il contratto del dipendente né lo stato del sito, perché quelle "
                    + "ore erano già state accettate quando sono state inserite. Se la riga non è annullata "
                    + "risponde 409. Riservato agli ADMIN.")
    public TimesheetResponse ripristina(@PathParam("id") Long id) {
        return TimesheetResponse.da(service.ripristina(id));
    }
}
