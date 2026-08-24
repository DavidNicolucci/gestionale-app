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
import java.util.List;

@Path("/api/dipendenti")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DipendenteResource {

    @Inject
    DipendenteService service;

    @GET
    @RolesAllowed({"ADMIN", "OPERATOR"})       // leggono entrambi i ruoli
    public List<DipendenteResponse> lista() {
        return service.listaTutti();
    }

    // Tabella paginata della home. E' un POST perche' i filtri stanno nel body:
    // sono troppi per metterli nell'indirizzo.
    @POST
    @Path("/ricerca")
    @RolesAllowed({"ADMIN", "OPERATOR"})
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
    public ScadenzeResponse scadenze() {
        return service.scadenze();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "OPERATOR"})
    public DipendenteResponse dettaglio(@PathParam("id") Long id) {
        return service.trovaPerId(id);
    }

    @POST
    @RolesAllowed("ADMIN")                      // crea solo l'ADMIN
    public DipendenteResponse crea(@Valid DipendenteRequest req) {
        return service.crea(req);
    }

    // PATCH e non PUT: si mandano solo i campi da cambiare, gli altri restano come sono.
    @PATCH
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public DipendenteResponse aggiorna(@PathParam("id") Long id, @Valid DipendentePatchRequest req) {
        return service.aggiorna(id, req);
    }

    // La cancellazione e' logica: la riga resta, il dipendente sparisce da elenchi e
    // ricerche. Serve perche' i timesheet lo referenziano e le ore gia' consuntivate
    // non devono sparire con lui. Per rimetterlo in anagrafica c'e' /ripristino.
    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")                      // cancella solo l'ADMIN
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
    public DipendenteResponse ripristina(@PathParam("id") Long id) {
        return service.ripristina(id);
    }

    // Il rinnovo si usa anche in anticipo, su un contratto ancora valido: e' il caso
    // normale. Su uno gia' scaduto e' l'unica strada per renderlo di nuovo utilizzabile.
    @POST
    @Path("/{id}/rinnovo")
    @RolesAllowed("ADMIN")
    public DipendenteResponse rinnova(@PathParam("id") Long id, @Valid RinnovoRequest req) {
        return service.rinnova(id, req);
    }
}
