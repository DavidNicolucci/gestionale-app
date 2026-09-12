package com.gestionale.dominio.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestionale.dominio.observability.MetricheImport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Svuota import.queue.dlq e scrive sul registro che quel file e' finito male.
 *
 * PERCHE' ESISTE. La DLQ era costruita bene - DLX dichiarato, failure-strategy=reject,
 * niente messaggi persi o code bloccate - ma era un posto dove non guardava nessuno.
 * Un import saltato di notte lasciava un messaggio li' dentro e un file sul disco, e
 * nessuna delle due cose bussava a qualcuno.
 *
 * COSA CAMBIA. Il messaggio rigettato non resta in DLQ ad accumularsi: viene letto qui,
 * e quello che sopravvive e' la riga di import_job, che dice quale file, quando, perche',
 * a che riga si era arrivati e se il file e' ancora rilanciabile. E' un registro che si
 * interroga (GET /api/admin/import), che si filtra e da cui si rilancia; una coda di
 * messaggi opachi non e' nessuna di queste cose.
 *
 * IL SECONDO TENTATIVO DI SCRITTURA. Nel caso tipico e' il consumer principale ad aver
 * gia' segnato il job come FALLITO. Ma se a saltare era proprio il database, quella
 * scritta non e' arrivata: qui ci riproviamo qualche istante dopo, quando spesso il
 * database e' tornato. E' per questo che il metodo e' scritto per essere innocuo se il
 * job e' gia' a posto.
 *
 * NON RILANCIA MAI. Un'eccezione da qui rimetterebbe il messaggio nella stessa coda di
 * scarto da cui e' appena uscito, cioe' un giro infinito: qualunque problema si logga e
 * si va avanti.
 */
@ApplicationScoped
public class ImportDlqConsumer {

    private static final Logger LOG = Logger.getLogger(ImportDlqConsumer.class);

    @Inject ObjectMapper objectMapper;
    @Inject ImportJobService jobService;
    @Inject MetricheImport metriche;

    @Incoming("import-dlq")
    public void scartato(String jsonMessage) {
        metriche.messaggioInDlq();
        try {
            ImportMessage msg = objectMapper.readValue(jsonMessage, ImportMessage.class);

            if (msg.jobId == null) {
                // Messaggio vecchio, senza registro: qui non si puo' fare di meglio che
                // lasciarne traccia nei log, insieme al percorso del file che resta su
                // disco e che qualcuno puo' ancora ricaricare a mano.
                LOG.errorf("Import in coda di scarto senza jobId: file=%s, percorso=%s",
                        msg.fileName, msg.filePath);
                return;
            }

            LOG.warnf("Import %d arrivato in coda di scarto (file=%s)", msg.jobId, msg.fileName);
            jobService.segnaFallito(msg.jobId,
                    "Messaggio rigettato e finito in import.queue.dlq."
                            + " Il dettaglio dell'errore e' nei log del tentativo.");

        } catch (Exception e) {
            LOG.errorf(e, "Messaggio in coda di scarto non interpretabile: %s", jsonMessage);
        }
    }
}
