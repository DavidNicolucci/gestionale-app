package com.gestionale.dominio.imports;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * Il cruscotto degli import: e' quello che rende utile la coda di scarto.
 *
 * La rete di sicurezza c'era gia' (DLX dichiarato, failure-strategy=reject, niente
 * messaggi persi e niente code bloccate), ma finiva in un posto dove non guardava
 * nessuno. Se stanotte il database ha un singhiozzo a meta' import, il messaggio finisce
 * in DLQ e il file resta sul disco: senza questi endpoint la cosa si scopre quando
 * qualcuno nota che mancano delle ore, cioe' tardi.
 *
 * Riservato agli ADMIN: qui si rimettono in moto scritture di massa e si cancellano
 * file, che non e' roba da OPERATOR.
 */
@Path("/api/admin/import")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Import (admin)", description = "Controllo degli import: cosa e' fallito e come rimetterlo in moto")
public class ImportAdminResource {

    /** Tetto al numero di righe restituite, per non doversi difendere da limite=1000000. */
    private static final int LIMITE_MASSIMO = 500;
    private static final int LIMITE_DEFAULT = 50;

    @Inject
    ImportAdminService service;

    @GET
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Elenco degli import da guardare",
            description = "Senza parametri restituisce gli import che NON sono andati a buon fine, dal piu' "
                    + "recente: i FALLITO, ma anche quelli rimasti in ACCODATO o IN_CORSO, che sono la forma "
                    + "silenziosa dello stesso problema (un job fermo in IN_CORSO da ore vuol dire che il "
                    + "consumer e' morto a meta' lavoro). Con stato=... si chiede un singolo stato, con "
                    + "stato=TUTTI l'intero storico. Il campo \"ritentabile\" dice se si puo' chiamare il "
                    + "rilancio senza forzarlo.")
    public List<ImportJobResponse> elenco(@QueryParam("stato") String stato,
                                          @QueryParam("limite") @DefaultValue("" + LIMITE_DEFAULT) int limite) {
        int quanti = Math.clamp(limite, 1, LIMITE_MASSIMO);

        if (stato == null || stato.isBlank()) {
            return service.elenco(null, quanti);
        }
        if ("TUTTI".equalsIgnoreCase(stato)) {
            return service.tutti(quanti);
        }
        return service.elenco(interpreta(stato), quanti);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Dettaglio di un import",
            description = "Tutto quello che si sa di un caricamento: stato, tentativi, righe inserite, "
                    + "aggiornate e scartate, l'ultima riga del foglio gia' salvata e il motivo dell'errore. "
                    + "Se l'id non esiste risponde 404.")
    public ImportJobResponse dettaglio(@PathParam("id") long id) {
        return service.dettaglio(id);
    }

    @POST
    @Path("/{id}/rilancia")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Rimetti in coda un import fallito",
            description = "Riaccoda il file, che viene rielaborato come un caricamento normale. "
                    + "Riparte dall'ultima riga gia' salvata, quindi un file interrotto a meta' non "
                    + "ricomincia da capo; con dallInizio=true si rilegge tutto il foglio. "
                    + "In nessuno dei due casi le ore vengono raddoppiate: una riga gia' presente per "
                    + "quel dipendente, quel sito e quel giorno viene corretta, non duplicata. "
                    + "Risponde 409 se il file non e' piu' sul server, o se lo stato non e' FALLITO "
                    + "(in quel caso serve forza=true).")
    public ImportJobResponse rilancia(@PathParam("id") long id,
                                      @QueryParam("dallInizio") @DefaultValue("false") boolean dallInizio,
                                      @QueryParam("forza") @DefaultValue("false") boolean forza) {
        return service.rilancia(id, dallInizio, forza);
    }

    @POST
    @Path("/{id}/abbandona")
    @RolesAllowed("ADMIN")
    @Operation(
            summary = "Rinuncia a un import",
            description = "Cancella il file dal server e segna l'import come ABBANDONATO, con il motivo "
                    + "indicato. Si usa quando il file e' sbagliato e non c'e' niente da recuperare: serve "
                    + "a togliere la riga dall'elenco di quelli da guardare, che altrimenti diventa lui "
                    + "stesso un posto pieno di roba vecchia. La riga resta a database come traccia. "
                    + "Risponde 409 su un import gia' andato a buon fine.")
    public ImportJobResponse abbandona(@PathParam("id") long id, MotivoAbbandono motivo) {
        return service.abbandona(id, motivo == null ? null : motivo.motivo);
    }

    private StatoImport interpreta(String stato) {
        try {
            return StatoImport.valueOf(stato.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // Il messaggio elenca i valori buoni: "stato non valido" e basta obbligherebbe
            // a cercarli nel codice.
            throw new BadRequestException("Stato \"" + stato + "\" non valido. Valori ammessi: "
                    + java.util.Arrays.toString(StatoImport.values()) + " oppure TUTTI");
        }
    }

    /** Il body dell'abbandono. Facoltativo, ma scriverci il perche' aiuta chi legge dopo. */
    public static class MotivoAbbandono {
        public String motivo;
    }
}
