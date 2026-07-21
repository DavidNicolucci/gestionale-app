package com.gestionale.dominio.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.model.entity.Sito;
import com.gestionale.dominio.model.entity.Timesheet;
import com.gestionale.dominio.repository.DipendenteRepository;
import com.gestionale.dominio.repository.SitoRepository;
import com.gestionale.dominio.repository.TimesheetRepository;
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

    @Incoming("import-in")              // ASCOLTA il canale in entrata: scatta a ogni messaggio
    @Transactional
    public void elabora(String jsonMessage) {
        String filePathToDelete = null;
        boolean isDbOrTxError = false;
        try {
            ImportMessage msg = objectMapper.readValue(jsonMessage, ImportMessage.class);
            filePathToDelete = msg.filePath;
            LOG.infof("Inizio import del file: %s", msg.fileName);

            int righeOk = 0;
            int righeErrore = 0;

            try (FileInputStream fis = new FileInputStream(msg.filePath);
                 Workbook workbook = WorkbookFactory.create(fis)) {

                Sheet sheet = workbook.getSheetAt(0);   // primo foglio

                // Parte da 1 per saltare la riga di intestazione (riga 0)
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    try {
                        String cf       = getString(row.getCell(0));
                        String nomeSito = getString(row.getCell(1));
                        LocalDate data  = getData(row.getCell(2));
                        BigDecimal ore  = getNumero(row.getCell(3));

                        // Lookup: il dipendente e il sito devono esistere.
                        // Optional dai repository -> niente null check, ma il caso "assente"
                        // resta un errore di DATO (riga saltata), non di infrastruttura.
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
                        if (isDatabaseOrTransactionException(e)) {
                            isDbOrTxError = true;
                            throw e; // Rilancia per attivare il fail-fast ed uscire subito
                        }
                        LOG.warnf("Errore sulla riga %d: %s", i, e.getMessage());
                        righeErrore++;
                    }
                }
            }

            LOG.infof("Import completato (%s): %d righe inserite, %d errori",
                    msg.fileName, righeOk, righeErrore);

            // Pulizia: rimuove il file temporaneo dopo l'elaborazione con successo
            if (filePathToDelete != null) {
                Files.deleteIfExists(Paths.get(filePathToDelete));
            }

        } catch (Exception e) {
            LOG.errorf(e, "Errore fatale nell'elaborazione del messaggio di import. DB Error? %b", isDbOrTxError);

            // Se l'errore NON è di DB/transazionale (es. file corrotto o non leggibile):
            // Rimuoviamo il file temporaneo per evitare perdite di spazio su disco
            if (!isDbOrTxError && filePathToDelete != null) {
                try {
                    Files.deleteIfExists(Paths.get(filePathToDelete));
                } catch (java.io.IOException ioException) {
                    LOG.error("Impossibile eliminare il file corrotto", ioException);
                }
            }

            // Rilanciamo l'eccezione per notificare il fallimento al broker (Nack)
            throw new RuntimeException("Errore fatale durante l'elaborazione dell'import", e);
        }
    }

    // Helper: identifica se una eccezione o la sua causa è riconducibile al Database o a Transazioni
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

    // Helper: legge una cella come stringa in modo sicuro
    private String getString(Cell cell) {
        if (cell == null) return null;
        return cell.getStringCellValue().trim();
    }

    // Legge una cella come numero, accettando sia celle numeriche sia testo "7.5"
    private BigDecimal getNumero(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        // se è testo, lo converto (gestisco anche la virgola decimale all'italiana)
        String raw = cell.getStringCellValue().trim().replace(",", ".");
        return new BigDecimal(raw);
    }

    // Legge una cella come data, accettando sia date Excel sia testo "2025-08-01"
    private LocalDate getData(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        // se è testo, parso il formato ISO (yyyy-MM-dd)
        String raw = cell.getStringCellValue().trim();
        return LocalDate.parse(raw);
    }
}