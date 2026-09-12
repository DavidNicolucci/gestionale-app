package com.gestionale.dominio.imports;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Quello che un ADMIN puo' fare a un import gia' passato: guardarlo, rilanciarlo,
 * chiuderlo.
 *
 * L'orchestrazione sta qui e le transazioni in ImportJobService, separate di proposito:
 * il rilancio deve prima committare il cambio di stato e POI mettere il messaggio in
 * coda. Se le due cose stessero nella stessa transazione, un consumer veloce potrebbe
 * prendere il messaggio prima del commit e trovare il job ancora in stato FALLITO.
 * Nell'ordine giusto il caso peggiore e' un job rimasto ACCODATO senza messaggio, che si
 * vede nell'elenco e si rilancia di nuovo.
 */
@ApplicationScoped
public class ImportAdminService {

    private static final Logger LOG = Logger.getLogger(ImportAdminService.class);

    @Inject ImportJobService jobService;
    @Inject CodaImport coda;

    public List<ImportJobResponse> elenco(StatoImport stato, int limite) {
        List<ImportJob> jobs = (stato == null)
                ? jobService.daGuardare(limite)     // il default: quelli che non sono finiti bene
                : jobService.perStato(stato, limite);
        return jobs.stream().map(ImportJobResponse::da).toList();
    }

    public List<ImportJobResponse> tutti(int limite) {
        return jobService.ultimi(limite).stream().map(ImportJobResponse::da).toList();
    }

    public ImportJobResponse dettaglio(long id) {
        return ImportJobResponse.da(jobService.perId(id));
    }

    /**
     * Rimette il file in coda.
     *
     * Due controlli prima di farlo, tutti e due con un messaggio che dice cosa fare:
     *
     * - lo stato. FALLITO si rilancia sempre. ACCODATO e IN_CORSO no, perche' potrebbero
     *   essere ancora vivi e si finirebbe con due elaborazioni sullo stesso file; si
     *   forzano quando si sa che il consumer e' morto (un IN_CORSO fermo da ore).
     *   COMPLETATO e ABBANDONATO nemmeno, perche' il file non c'e' piu'.
     *
     * - il file. Senza file non c'e' niente da rileggere, e nessuna forzatura lo fa
     *   ricomparire: qui la risposta e' "ricaricalo dall'upload", non un tentativo che
     *   fallirebbe fra due secondi.
     *
     * Rilanciare non raddoppia le ore: l'import riparte dall'ultima riga committata e
     * comunque le righe gia' presenti vengono corrette, non duplicate.
     */
    public ImportJobResponse rilancia(long id, boolean dallInizio, boolean forza) {
        ImportJob job = jobService.perId(id);

        if (!job.stato.ritentabile() && !forza) {
            throw new WebApplicationException(
                    "L'import " + id + " e' in stato " + job.stato + " e non si rilancia."
                            + motivazione(job.stato)
                            + " Per farlo comunque, ripetere con forza=true", 409);
        }
        if (!job.fileDisponibile()) {
            throw new WebApplicationException(
                    "L'import " + id + " non si puo' rilanciare: il file " + job.fileName
                            + " non e' piu' sul server. Ricaricalo da POST /api/import/timesheet", 409);
        }

        ImportJob rilanciato = jobService.preparaRilancio(id, dallInizio);
        coda.accoda(rilanciato);

        LOG.infof("Import %d rilanciato da un ADMIN (%s, tentativi finora: %d)",
                id, dallInizio ? "dall'inizio" : "dalla riga " + rilanciato.ultimaRiga,
                rilanciato.tentativi);
        return ImportJobResponse.da(rilanciato);
    }

    /**
     * Chiude la pratica: il file viene cancellato e il job segnato ABBANDONATO.
     *
     * Serve perche' un elenco di falliti che non si svuota mai diventa la stessa cosa
     * che era la DLQ: un posto pieno di roba che nessuno guarda piu'. Qui invece
     * l'abbandono e' una decisione esplicita di qualcuno, e resta scritta con il suo
     * motivo. La riga non si cancella: e' la traccia che quel file era arrivato.
     */
    public ImportJobResponse abbandona(long id, String motivo) {
        ImportJob job = jobService.perId(id);

        if (job.stato == StatoImport.COMPLETATO) {
            throw new WebApplicationException(
                    "L'import " + id + " e' andato a buon fine: non c'e' niente da abbandonare", 409);
        }

        cancellaFile(job);
        ImportJob abbandonato = jobService.segnaAbbandonato(id,
                motivo == null || motivo.isBlank()
                        ? "Abbandonato da un ADMIN"
                        : "Abbandonato da un ADMIN: " + motivo);

        LOG.infof("Import %d abbandonato: %s", id, abbandonato.errore);
        return ImportJobResponse.da(abbandonato);
    }

    private void cancellaFile(ImportJob job) {
        try {
            if (Files.deleteIfExists(Paths.get(job.filePath))) {
                LOG.infof("File dell'import %d cancellato: %s", job.id, job.filePath);
            }
        } catch (IOException e) {
            // Il file resta sul disco ma la decisione dell'ADMIN vale lo stesso: meglio
            // un file orfano da togliere a mano che un abbandono che non si riesce a fare.
            LOG.warnf("Import %d abbandonato ma il file %s non si riesce a cancellare: %s",
                    job.id, job.filePath, e.getMessage());
        }
    }

    private String motivazione(StatoImport stato) {
        return switch (stato) {
            case ACCODATO -> " Il messaggio potrebbe essere ancora in coda e partire da solo.";
            case IN_CORSO -> " Un consumer potrebbe starci lavorando adesso.";
            case COMPLETATO -> " E' gia' andato a buon fine.";
            case ABBANDONATO -> " E' stato abbandonato e il file non c'e' piu'.";
            case FALLITO -> "";
        };
    }
}
