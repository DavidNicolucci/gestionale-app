package com.gestionale.dominio.resources;

import com.gestionale.dominio.model.dto.DipendentePatchRequest;
import com.gestionale.dominio.model.dto.DipendenteRequest;
import com.gestionale.dominio.model.dto.DipendenteResponse;
import com.gestionale.dominio.model.dto.DipendenteRicercaRequest;
import com.gestionale.dominio.model.dto.PaginaResponse;
import com.gestionale.dominio.model.dto.RinnovoRequest;
import com.gestionale.dominio.model.dto.ScadenzeResponse;
import com.gestionale.dominio.service.DipendenteService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.List;

@Path("/api/dipendenti")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Dipendenti", description = "Anagrafica dei dipendenti e stato dei loro contratti")
public class DipendenteResource {

    @Inject
    DipendenteService service;

    @GET
    @RolesAllowed({"ADMIN", "OPERATOR"})       // leggono entrambi i ruoli
    @Operation(
            summary = "Elenco di tutti i dipendenti",
            description = "Restituisce l'elenco completo dei dipendenti attivi, senza filtri e senza pagine. "
                    + "Utile per riempire le tendine di scelta. Per la tabella con filtri usare /api/dipendenti/ricerca.")
    public List<DipendenteResponse> lista() {
        return service.listaTutti();
    }

    // Tabella paginata della home. E' un POST perche' i filtri stanno nel body:
    // sono troppi per metterli nell'indirizzo.
    @POST
    @Path("/ricerca")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Cerca i dipendenti a pagine",
            description = "Restituisce una pagina di dipendenti in base ai filtri e all'ordinamento indicati nel "
                    + "corpo della richiesta. E' una POST solo perche' i filtri sono troppi per stare nell'indirizzo. "
                    + "Corpo vuoto: prima pagina, nessun filtro.")
    public PaginaResponse<DipendenteResponse> ricerca(DipendenteRicercaRequest req) {
        return service.cerca(req != null ? req : new DipendenteRicercaRequest());
    }

    // Quanti contratti stanno per scadere, per l'avviso sopra la tabella.
    // Il pezzo fisso della rotta va dichiarato prima di "/{id}" solo per leggibilita':
    // a decidere e' JAX-RS, che a parita' di richiesta preferisce sempre il segmento
    // scritto per esteso al segnaposto, indipendentemente dall'ordine dei metodi.
    @GET
    @Path("/scadenze")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Contratti scaduti o in scadenza",
            description = "Restituisce il riepilogo dei contratti gia' scaduti e di quelli che stanno per scadere. "
                    + "Serve all'avviso mostrato sopra la tabella dei dipendenti.")
    public ScadenzeResponse scadenze() {
        return service.scadenze();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Dettaglio di un dipendente",
            description = "Restituisce tutti i dati del dipendente con l'id indicato. Se l'id non esiste risponde 404.")
    public DipendenteResponse dettaglio(@PathParam("id") Long id) {
        return service.trovaPerId(id);
    }

    @POST
    @RolesAllowed("ADMIN")                      // crea solo l'ADMIN
    @Operation(
            summary = "Crea un nuovo dipendente",
            description = "Aggiunge un dipendente all'anagrafica e restituisce i dati salvati, id compreso. "
                    + "Riservato agli ADMIN.")
    public DipendenteResponse crea(@Valid DipendenteRequest req) {
        return service.crea(req);
    }

    // PATCH e non PUT: si mandano solo i campi da cambiare, gli altri restano come sono.
    @PATCH
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Modifica un dipendente",
            description = "Aggiorna solo i campi presenti nella richiesta: quelli che non vengono inviati restano "
                    + "come sono. Per portare un contratto a tempo indeterminato non basta omettere la scadenza "
                    + "(sarebbe un \"non toccarla\"): serve inviare rimuoviScadenza a true. Un dipendente scaduto "
                    + "o eliminato non si modifica (409): prima va rinnovato o ripristinato. "
                    + "Restituisce il dipendente aggiornato. Riservato agli ADMIN.")
    public DipendenteResponse aggiorna(@PathParam("id") Long id, @Valid DipendentePatchRequest req) {
        return service.aggiorna(id, req);
    }

    // La cancellazione e' logica: la riga resta, il dipendente sparisce da elenchi e
    // ricerche. Serve perche' i timesheet lo referenziano e le ore gia' consuntivate
    // non devono sparire con lui. Per rimetterlo in anagrafica c'e' /ripristino.
    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")                      // cancella solo l'ADMIN
    @Operation(
            summary = "Elimina un dipendente",
            description = "Il dipendente sparisce da elenchi e ricerche, ma i suoi dati e le ore gia' registrate "
                    + "restano salvati: la cancellazione e' solo logica. Per rimetterlo in servizio usare "
                    + "/api/dipendenti/{id}/ripristino. Non restituisce nulla (204). Riservato agli ADMIN.")
    public void elimina(@PathParam("id") Long id) {
        service.elimina(id);
    }

    // Le due operazioni che riportano in servizio un dipendente. Sono POST su una
    // sotto-rotta e non due PATCH particolari perche' non sono modifiche di campi:
    // sono gesti che cambiano quello che il dipendente puo' fare, e vanno chiamati
    // per nome. Cosi' si leggono nei log e potranno avere permessi loro.
    @POST
    @Path("/{id}/ripristino")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Ripristina un dipendente eliminato",
            description = "Riporta in anagrafica un dipendente che era stato eliminato: torna a comparire negli "
                    + "elenchi e nelle ricerche. Restituisce il dipendente ripristinato. Riservato agli ADMIN.")
    public DipendenteResponse ripristina(@PathParam("id") Long id) {
        return service.ripristina(id);
    }

    // Il rinnovo si usa anche in anticipo, su un contratto ancora valido: e' il caso
    // normale. Su uno gia' scaduto e' l'unica strada per renderlo di nuovo utilizzabile.
    @POST
    @Path("/{id}/rinnovo")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Rinnova il contratto di un dipendente",
            description = "Sposta in avanti la scadenza del contratto alla nuova data indicata. Si usa sia in "
                    + "anticipo, su un contratto ancora valido, sia su uno gia' scaduto per rendere di nuovo "
                    + "utilizzabile il dipendente. Riservato agli ADMIN.")
    public DipendenteResponse rinnova(@PathParam("id") Long id, @Valid RinnovoRequest req) {
        return service.rinnova(id, req);
    }
}
