package com.gestionale.dominio.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestionale.dominio.observability.MetricheImport;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.poi.ss.usermodel.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Legge i file di ore messi in coda e li porta a database.
 *
 * COM'E' FATTO, e perche' non e' piu' un metodo solo.
 *
 * La lettura del foglio avviene FUORI da qualunque transazione, il salvataggio a
 * BLOCCHI, ognuno con la sua transazione (vedi ImportEsecutore). Prima c'era un solo
 * @Transactional su tutto il metodo, e su un file da 5.000 righe questo voleva dire una
 * transazione aperta per minuti - con una connessione del pool occupata per tutto il
 * tempo - e un errore alla riga 4.999 che annullava anche le 4.998 righe buone.
 *
 * Sugli errori di CIRCOSTANZA (connessione caduta per due secondi, deadlock, lock
 * timeout) il blocco viene riprovato dopo un'attesa che raddoppia a ogni tentativo.
 * failure-strategy=reject manda il messaggio in DLQ al primo fallimento, e per un file
 * rovinato e' la cosa giusta - riprovare non serve - ma per un database che ha avuto un
 * singhiozzo buttare via subito e' severo: un solo tentativo dopo qualche secondo lo
 * avrebbe recuperato. La riprova sta qui e non nel broker perche' qui sappiamo DI COSA
 * si tratta: chi decide e' ErroriTransitori, e il criterio e' "si riprova solo cio' che
 * riconosciamo come passeggero", mai il contrario.
 *
 * Esaurite le riprove il messaggio viene comunque rigettato e finisce in DLQ, ma con
 * due differenze rispetto a prima: le righe gia' salvate restano salvate, e su
 * import_job resta scritto cos'e' successo e a che riga era arrivato. Da li' ripartono
 * l'elenco e il rilancio di ImportAdminResource.
 */
@ApplicationScoped
public class ImportConsumer {

    private static final Logger LOG = Logger.getLogger(ImportConsumer.class);

    @Inject ObjectMapper objectMapper;
    @Inject ImportEsecutore esecutore;
    @Inject ImportJobService jobService;
    @Inject MetricheImport metriche;

    /**
     * Quante righe per transazione. E' un compromesso: piu' grande vuol dire meno
     * commit ma transazioni piu' lunghe e piu' lavoro perso quando un blocco salta;
     * piu' piccolo vuol dire il contrario.
     */
    @ConfigProperty(name = "import.blocco-righe", defaultValue = "500")
    int dimensioneBlocco;

    /** Tentativi totali su un blocco, il primo compreso. A 1 la riprova e' spenta. */
    @ConfigProperty(name = "import.riprove", defaultValue = "3")
    int tentativiMassimi;

    /** Attesa prima della prima riprova; raddoppia a ogni tentativo successivo. */
    @ConfigProperty(name = "import.attesa-riprova", defaultValue = "PT2S")
    Duration attesaIniziale;

    // @WithSpan apre uno span figlio di quello che il connettore RabbitMQ crea alla
    // ricezione del messaggio. Il contesto di trace viaggia negli header AMQP, quindi
    // la trace e' la stessa che era partita dalla POST /api/import/timesheet: in Jaeger
    // si vede l'upload e l'elaborazione, avvenuta dopo e su un altro thread, in un'unica
    // riga temporale.
    //
    // NIENTE @Transactional: le transazioni le apre ImportEsecutore, un blocco per volta.
    @Incoming("import-in")              // resta in ascolto sulla coda: parte a ogni messaggio
    @WithSpan("import timesheet")
    public void elabora(String jsonMessage) {
        Timer.Sample cronometro = metriche.avviaCronometro();

        ImportMessage msg;
        try {
            msg = objectMapper.readValue(jsonMessage, ImportMessage.class);
        } catch (Exception e) {
            // Messaggio illeggibile: non c'e' nessun job da aggiornare e non c'e' niente
            // da riprovare. Va in DLQ e ci resta, ed e' giusto cosi': e' l'unico caso in
            // cui la DLQ e' davvero l'ultimo posto dove guardare.
            LOG.errorf(e, "Messaggio di import illeggibile, va in coda di scarto: %s", jsonMessage);
            metriche.fileElaborato(MetricheImport.ESITO_ERRORE);
            metriche.ferma(cronometro, MetricheImport.ESITO_ERRORE);
            Span.current().recordException(e);
            throw new IllegalArgumentException("Messaggio di import illeggibile", e);
        }

        Optional<ImportJob> preso;
        try {
            preso = prendiInCarico(msg);
        } catch (RuntimeException e) {
            // Il database non risponde gia' al momento di prendere in carico il
            // messaggio. Le metriche vanno incrementate lo stesso: file.elaborati
            // {esito=errore} e' il contatore su cui si mette l'alert, e se restasse
            // fermo proprio nel caso del database giu' l'alert non scatterebbe mai.
            LOG.errorf(e, "Import %d: non si riesce nemmeno a prenderlo in carico", msg.jobId);
            metriche.fileElaborato(MetricheImport.ESITO_ERRORE);
            metriche.ferma(cronometro, MetricheImport.ESITO_ERRORE);
            Span.current().recordException(e);
            throw e;
        }

        if (preso.isEmpty()) {
            // Gia' importato o abbandonato: il messaggio si accetta e si butta. Nessuna
            // metrica di elaborazione, perche' non abbiamo elaborato niente.
            return;
        }
        ImportJob job = preso.get();

        Span span = Span.current();
        span.setAttribute("import.file_name", job.fileName);
        span.setAttribute("import.job_id", job.id);
        span.setAttribute("import.tentativo", job.tentativi);

        try {
            EsitoBlocco esito = importa(job);

            jobService.completa(job.id);
            span.setAttribute("import.righe_ok", esito.inserite() + esito.aggiornate());
            span.setAttribute("import.righe_scartate", esito.scartate());
            metriche.righe(MetricheImport.ESITO_OK, esito.inserite());
            metriche.righe(MetricheImport.ESITO_AGGIORNATA, esito.aggiornate());
            metriche.righe(MetricheImport.ESITO_SCARTATA, esito.scartate());
            metriche.fileElaborato(MetricheImport.ESITO_OK);
            metriche.ferma(cronometro, MetricheImport.ESITO_OK);

            LOG.infof("Import %d completato (%s): %d inserite, %d aggiornate, %d scartate",
                    job.id, job.fileName, esito.inserite(), esito.aggiornate(), esito.scartate());

            // Andato tutto bene: il file temporaneo non serve piu'.
            cancellaFile(job);

        } catch (Exception e) {
            LOG.errorf(e, "Import %d fallito sul file %s (tentativo %d, ultima riga salvata %d)",
                    job.id, job.fileName, job.tentativi, job.ultimaRiga);

            // Il file NON si cancella, nemmeno quando l'errore e' definitivo.
            // Prima si cancellava per non riempire il disco, ma senza il file non c'e'
            // niente da rilanciare e nemmeno niente da guardare per capire cos'era
            // rotto. A liberare il disco ci pensa adesso l'ADMIN, con
            // POST /api/admin/import/{id}/abbandona, che e' anche il momento in cui
            // qualcuno ha davvero deciso che quel file non serve piu'.
            jobService.segnaFallito(job.id, ErroriTransitori.descrivi(e));

            metriche.fileElaborato(MetricheImport.ESITO_ERRORE);
            metriche.ferma(cronometro, MetricheImport.ESITO_ERRORE);
            span.recordException(e);

            // Rilanciamo per dire a RabbitMQ che il messaggio non e' andato a buon fine:
            // con failure-strategy=reject viene dead-letterato su import.queue.dlq.
            throw new RuntimeException("Errore fatale durante l'elaborazione dell'import " + job.id, e);
        }
    }

    /**
     * Trova il job del messaggio, o ne apre uno adesso.
     *
     * Il ramo senza jobId serve ai messaggi accodati PRIMA che esistesse il registro:
     * al primo avvio dopo questa modifica, in import.queue (o gia' in DLQ) possono
     * essercene ancora. Scartarli sarebbe la scelta comoda e sbagliata - sono file di
     * ore veri, di cui nessuno ha avuto conferma - quindi gli si apre un job al volo e
     * proseguono come tutti gli altri.
     */
    private Optional<ImportJob> prendiInCarico(ImportMessage msg) {
        if (msg.jobId != null) {
            return jobService.prendiInCarico(msg.jobId);
        }

        LOG.warnf("Messaggio senza jobId (accodato prima del registro): apro un job per %s", msg.fileName);
        ImportJob job = jobService.registraUpload(
                msg.fileName, msg.filePath, improntaSePossibile(msg.filePath), null);
        return jobService.prendiInCarico(job.id);
    }

    // L'impronta serve a riconoscere il doppio caricamento, non a elaborare: se il file
    // non si riesce a leggere adesso, lo dira' fra un attimo la lettura del foglio con
    // un errore molto piu' chiaro di "non riesco a fare l'hash".
    private String improntaSePossibile(String filePath) {
        try {
            return ImprontaFile.sha256(Paths.get(filePath));
        } catch (IOException e) {
            LOG.warnf("Impronta non calcolabile per %s: %s", filePath, e.getMessage());
            return "sconosciuta";
        }
    }

    /** Legge il foglio e ne salva le righe a blocchi. */
    private EsitoBlocco importa(ImportJob job) throws IOException {
        List<RigaImport> righe = leggiFoglio(job);

        if (righe.isEmpty()) {
            LOG.infof("Import %d: nessuna riga da elaborare oltre la %d", job.id, job.ultimaRiga);
            return EsitoBlocco.VUOTO;
        }

        EsitoBlocco totale = EsitoBlocco.VUOTO;
        for (int da = 0; da < righe.size(); da += dimensioneBlocco) {
            List<RigaImport> blocco = righe.subList(da, Math.min(da + dimensioneBlocco, righe.size()));
            EsitoBlocco esito = conRiprova(
                    () -> esecutore.salvaBlocco(job.id, blocco),
                    "import " + job.id + ", righe " + blocco.get(0).numeroRiga()
                            + "-" + blocco.get(blocco.size() - 1).numeroRiga());
            totale = totale.piu(esito);
        }
        return totale;
    }

    /**
     * Esegue il salvataggio di un blocco, riprovando solo se l'errore e' passeggero.
     *
     * L'attesa raddoppia (2s, 4s, 8s...) invece di restare fissa: se il database sta
     * ripartendo, tre tentativi ravvicinati lo trovano giu' tutte e tre le volte e
     * tanto vale non averli fatti.
     */
    private <T> T conRiprova(Supplier<T> azione, String cosa) {
        long attesa = attesaIniziale.toMillis();
        for (int tentativo = 1; ; tentativo++) {
            try {
                return azione.get();
            } catch (RuntimeException e) {
                if (tentativo >= tentativiMassimi || !ErroriTransitori.transitorio(e)) {
                    throw e;
                }
                LOG.warnf("Errore passeggero su %s (tentativo %d di %d), riprovo fra %d ms: %s",
                        cosa, tentativo, tentativiMassimi, attesa, e.getMessage());
                metriche.riprova();
                attendi(attesa);
                attesa *= 2;
            }
        }
    }

    private void attendi(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // L'applicazione si sta fermando: si rimette il flag e si smette di insistere.
            // Il messaggio non e' stato accettato, quindi RabbitMQ lo riconsegnera'.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Import interrotto durante l'attesa fra due tentativi", e);
        }
    }

    /**
     * Converte il foglio in righe gia' pronte, senza toccare il database.
     *
     * Salta le righe fino a job.ultimaRiga: sono quelle che un tentativo precedente ha
     * gia' committato. E' questo che rende il rilancio economico invece di ricominciare
     * da capo ogni volta. Chi vuole rifare tutto usa il rilancio "dall'inizio", che
     * azzera il segnaposto.
     */
    private List<RigaImport> leggiFoglio(ImportJob job) throws IOException {
        List<RigaImport> righe = new ArrayList<>();
        int illeggibili = 0;

        try (FileInputStream fis = new FileInputStream(job.filePath);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);   // leggiamo solo il primo foglio

            // Si parte da 1 perche' la riga 0 e' quella dei titoli delle colonne
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                if (i <= job.ultimaRiga) {
                    continue;                       // gia' salvata da un tentativo precedente
                }
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    righe.add(new RigaImport(i,
                            getString(row.getCell(0)),
                            getString(row.getCell(1)),
                            getData(row.getCell(2)),
                            getNumero(row.getCell(3))));
                } catch (Exception e) {
                    // Una data scritta male o un numero che non e' un numero fermano la
                    // riga, non il file: un foglio da 500 righe non deve finire in coda
                    // di scarto per una cella sbagliata.
                    LOG.warnf("Riga %d illeggibile: %s", i, e.getMessage());
                    illeggibili++;
                }
            }
        }

        if (illeggibili > 0) {
            // Registrate subito e non alla fine: se un blocco piu' avanti fa fallire
            // tutto, questo numero deve essere gia' sul registro, altrimenti l'ADMIN
            // vede un import fallito con zero righe scartate e nessun indizio.
            jobService.aggiungiScartate(job.id, illeggibili);
            metriche.righe(MetricheImport.ESITO_SCARTATA, illeggibili);
        }

        LOG.infof("Import %d: %d righe da elaborare, %d illeggibili, si riparte dalla %d",
                job.id, righe.size(), illeggibili, job.ultimaRiga + 1);
        return righe;
    }

    private void cancellaFile(ImportJob job) {
        try {
            Files.deleteIfExists(Paths.get(job.filePath));
        } catch (IOException e) {
            // L'import e' andato bene: un file rimasto sul disco non lo rende fallito.
            LOG.warnf("Import %d completato ma il file %s non si riesce a cancellare: %s",
                    job.id, job.filePath, e.getMessage());
        }
    }

    // Legge una cella come testo, gestendo la cella vuota
    private String getString(Cell cell) {
        if (cell == null) return null;
        return cell.getStringCellValue().trim();
    }

    // Legge una cella come numero: va bene sia una cella numerica sia il testo "7.5"
    private BigDecimal getNumero(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        // se e' testo lo converto, cambiando la virgola in punto (7,5 -> 7.5)
        String raw = cell.getStringCellValue().trim().replace(",", ".");
        return new BigDecimal(raw);
    }

    // Legge una cella come data: va bene sia la data di Excel sia il testo "2025-08-01"
    private LocalDate getData(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        // se e' testo mi aspetto il formato anno-mese-giorno
        String raw = cell.getStringCellValue().trim();
        return LocalDate.parse(raw);
    }
}
