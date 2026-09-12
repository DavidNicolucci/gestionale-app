package com.gestionale.dominio.imports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gestionale.dominio.observability.MetricheImport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * L'unico punto da cui parte un messaggio di import.
 *
 * Ci passano l'upload (prima volta) e il rilancio dall'endpoint admin (le volte dopo).
 * Stanno insieme perche' devono mettere in coda esattamente lo stesso messaggio: se il
 * rilancio ne costruisse uno suo, basterebbe un campo dimenticato per avere un import
 * che si comporta diversamente a seconda di chi l'ha fatto partire.
 */
@ApplicationScoped
public class CodaImport {

    private static final Logger LOG = Logger.getLogger(CodaImport.class);

    @Inject
    @Channel("import-out")                 // la coda configurata in application.properties
    Emitter<String> emitter;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MetricheImport metriche;

    public void accoda(ImportJob job) {
        ImportMessage msg = new ImportMessage(job.filePath, job.fileName, job.id);
        try {
            emitter.send(objectMapper.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            // Tre campi di testo non producono un JSON invalido: se succede e' un bug,
            // non una condizione da gestire.
            throw new IllegalStateException("Impossibile serializzare il messaggio di import", e);
        }

        // Il contatore degli accodati va letto insieme a quello degli elaborati: se il
        // primo cresce e il secondo no, i messaggi si stanno fermando in coda o stanno
        // finendo in DLQ, e nessuno dei due casi produce un errore HTTP.
        metriche.fileAccodato();
        LOG.infof("Import %d accodato: file=%s", job.id, job.fileName);
    }
}
