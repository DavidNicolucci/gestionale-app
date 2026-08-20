package com.gestionale.dominio.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.model.entity.Sito;
import com.gestionale.dominio.model.entity.Timesheet;
import com.gestionale.dominio.observability.MetricheImport;
import com.gestionale.dominio.repository.DipendenteRepository;
import com.gestionale.dominio.repository.SitoRepository;
import com.gestionale.dominio.repository.TimesheetRepository;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.*;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Optional;

@ApplicationScoped
public class ImportConsumer {

    private static final Logger LOG = Logger.getLogger(ImportConsumer.class);

    @Inject ObjectMapper objectMapper;
    @Inject DipendenteRepository dipendenteRepo;
    @Inject SitoRepository sitoRepo;
    @Inject TimesheetRepository timesheetRepo;
    @Inject MetricheImport metriche;

    // @WithSpan apre uno span figlio di quello che il connettore RabbitMQ crea alla
    // ricezione del messaggio. Il contesto di trace viaggia negli header AMQP, quindi
    // la trace e' la stessa che era partita dalla POST /api/import/timesheet: in Jaeger
    // si vede l'upload e l'elaborazione, avvenuta dopo e su un altro thread, in un'unica
    // riga temporale.
    @Incoming("import-in")              // resta in ascolto sulla coda: parte a ogni messaggio
    @Transactional
    @WithSpan("import timesheet")
    public void elabora(String jsonMessage) {
        String filePathToDelete = null;
        boolean isDbOrTxError = false;
        Timer.Sample cronometro = metriche.avviaCronometro();
        try {
            ImportMessage msg = objectMapper.readValue(jsonMessage, ImportMessage.class);
            filePathToDelete = msg.filePath;
            LOG.infof("Inizio import del file: %s", msg.fileName);

            // Attributi sullo span: sono quelli che in Jaeger permettono di ritrovare
            // la trace di UN import preciso invece di scorrerle tutte a mano.
            Span span = Span.current();
            span.setAttribute("import.file_name", msg.fileName);

            int righeOk = 0;
            int righeErrore = 0;

            try (FileInputStream fis = new FileInputStream(msg.filePath);
                 Workbook workbook = WorkbookFactory.create(fis)) {

                Sheet sheet = workbook.getSheetAt(0);   // leggiamo solo il primo foglio

                // Si parte da 1 perche' la riga 0 e' quella dei titoli delle colonne
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    try {
                        String cf       = getString(row.getCell(0));
                        String nomeSito = getString(row.getCell(1));
                        LocalDate data  = getData(row.getCell(2));
                        BigDecimal ore  = getNumero(row.getCell(3));

                        // Dipendente e sito devono gia' esistere: se non li troviamo
                        // e' un errore nei dati del file, saltiamo la riga e andiamo avanti.
                        Optional<Dipendente> dip = dipendenteRepo.perCodiceFiscale(cf);
                        Optional<Sito> sito = sitoRepo.perNome(nomeSito);

                        if (dip.isEmpty() || sito.isEmpty()) {
                            LOG.warnf("Riga %d ignorata: dipendente o sito non trovato (cf=%s, sito=%s)",
                                    i, cf, nomeSito);
                            righeErrore++;
                            continue;
                        }

                        Timesheet ts = new Timesheet();
                        ts.dipendente = dip.get();
                        ts.sito = sito.get();
                        ts.dataLavoro = data;
                        ts.oreLavorate = ore;
                        timesheetRepo.persist(ts);
                        righeOk++;

                    } catch (Exception e) {
                        // Se il problema e' il database inutile continuare con le altre
                        // righe: fermiamo tutto subito.
                        if (isDatabaseOrTransactionException(e)) {
                            isDbOrTxError = true;
                            throw e;
                        }
                        LOG.warnf("Errore sulla riga %d: %s", i, e.getMessage());
                        righeErrore++;
                    }
                }
            }

            LOG.infof("Import completato (%s): %d righe inserite, %d errori",
                    msg.fileName, righeOk, righeErrore);

            span.setAttribute("import.righe_ok", righeOk);
            span.setAttribute("import.righe_scartate", righeErrore);
            metriche.righe(MetricheImport.ESITO_OK, righeOk);
            metriche.righe(MetricheImport.ESITO_SCARTATA, righeErrore);
            metriche.fileElaborato(MetricheImport.ESITO_OK);
            metriche.ferma(cronometro, MetricheImport.ESITO_OK);

            // Andato tutto bene: cancelliamo il file temporaneo
            if (filePathToDelete != null) {
                Files.deleteIfExists(Paths.get(filePathToDelete));
            }

        } catch (Exception e) {
            LOG.errorf(e, "Errore fatale nell'elaborazione del messaggio di import. DB Error? %b", isDbOrTxError);

            // Le righe inserite finora non le contiamo: la transazione sta per essere
            // annullata, quindi a database non ne resta nessuna e un contatore che le
            // includesse racconterebbe di lavoro mai fatto.
            metriche.fileElaborato(MetricheImport.ESITO_ERRORE);
            metriche.ferma(cronometro, MetricheImport.ESITO_ERRORE);
            Span.current().recordException(e);

            // Se il problema non e' il database (es. file rovinato) il file non serve
            // piu' a niente: lo cancelliamo per non riempire il disco.
            // Se invece e' il database lo teniamo, cosi' si puo' riprovare.
            if (!isDbOrTxError && filePathToDelete != null) {
                try {
                    Files.deleteIfExists(Paths.get(filePathToDelete));
                } catch (java.io.IOException ioException) {
                    LOG.error("Impossibile eliminare il file corrotto", ioException);
                }
            }

            // Rilanciamo l'errore per dire a RabbitMQ che il messaggio non e' andato a buon fine
            throw new RuntimeException("Errore fatale durante l'elaborazione dell'import", e);
        }
    }

    // Guarda l'errore e tutti quelli dentro di lui per capire se arriva dal database.
    private boolean isDatabaseOrTransactionException(Throwable e) {
        if (e == null) return false;
        String className = e.getClass().getName();
        if (className.startsWith("jakarta.persistence") ||
            className.startsWith("org.hibernate") ||
            className.startsWith("java.sql") ||
            className.startsWith("jakarta.transaction")) {
            return true;
        }
        return isDatabaseOrTransactionException(e.getCause());
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