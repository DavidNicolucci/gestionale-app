package com.gestionale.dominio.resources;

import com.gestionale.dominio.model.dto.SitoPatchRequest;
import com.gestionale.dominio.model.dto.SitoRequest;
import com.gestionale.dominio.model.dto.SitoResponse;
import com.gestionale.dominio.model.dto.SitoRicercaRequest;
import com.gestionale.dominio.model.dto.PaginaResponse;
import com.gestionale.dominio.service.SitoService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.List;

@Path("/api/siti")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Siti", description = "I luoghi di lavoro (cantieri, sedi) collegati a un cliente")
public class SitoResource {

    @Inject
    SitoService service;

    @GET
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Elenco di tutti i siti",
            description = "Restituisce l'elenco completo dei siti, senza filtri e senza pagine. "
                    + "Utile per riempire le tendine di scelta. Per la tabella con filtri usare /api/siti/ricerca.")
    public List<SitoResponse> lista() {
        return service.listaTutti().stream().map(SitoResponse::da).toList();
    }

    // Tabella paginata della home. E' un POST perche' i filtri stanno nel body.
    @POST
    @Path("/ricerca")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Cerca i siti a pagine",
            description = "Restituisce una pagina di siti in base ai filtri e all'ordinamento indicati nel corpo "
                    + "della richiesta. E' una POST solo perche' i filtri sono troppi per stare nell'indirizzo. "
                    + "Corpo vuoto: prima pagina, nessun filtro.")
    public PaginaResponse<SitoResponse> ricerca(SitoRicercaRequest req) {
        return service.cerca(req != null ? req : new SitoRicercaRequest());
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    @Operation(
            summary = "Dettaglio di un sito",
            description = "Restituisce tutti i dati del sito con l'id indicato, cliente di appartenenza compreso. "
                    + "Se l'id non esiste risponde 404.")
    public SitoResponse dettaglio(@PathParam("id") Long id) {
        return SitoResponse.da(service.trovaPerId(id));
    }

    @POST
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Crea un nuovo sito",
            description = "Aggiunge un sito e lo collega al cliente indicato nella richiesta. Restituisce i dati "
                    + "salvati, id compreso. Se il cliente indicato non esiste risponde 404. Riservato agli ADMIN.")
    public SitoResponse crea(@Valid SitoRequest req) {
        return SitoResponse.da(service.crea(req));
    }

    // PATCH e non PUT: si mandano solo i campi da cambiare, gli altri restano come sono.
    @PATCH
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Modifica un sito",
            description = "Aggiorna solo i campi presenti nella richiesta: quelli che non vengono inviati restano "
                    + "come sono. Si puo' anche spostare il sito su un altro cliente. Riservato agli ADMIN.")
    public SitoResponse aggiorna(@PathParam("id") Long id, @Valid SitoPatchRequest req) {
        return SitoResponse.da(service.aggiorna(id, req));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Elimina un sito",
            description = "Il sito esce da elenchi e tendine e non accetta più ore nuove, ma le ore già "
                    + "registrate su di lui restano e continuano a contare nei totali: la cancellazione è solo "
                    + "logica. Ripetere la chiamata su un sito già eliminato non è un errore. Per rimetterlo in "
                    + "elenco usare /api/siti/{id}/ripristino. Non restituisce nulla (204). Riservato agli ADMIN.")
    public void elimina(@PathParam("id") Long id) {
        service.elimina(id);
    }

    @POST
    @Path("/{id}/ripristino")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Ripristina un sito eliminato",
            description = "Rimette il sito in elenco, così torna a comparire nelle tendine e ad accettare ore. "
                    + "Se il cliente a cui appartiene è a sua volta eliminato risponde 409: va ripristinato "
                    + "prima quello. Se il sito non è eliminato risponde 409. Riservato agli ADMIN.")
    public SitoResponse ripristina(@PathParam("id") Long id) {
        return SitoResponse.da(service.ripristina(id));
    }
}
