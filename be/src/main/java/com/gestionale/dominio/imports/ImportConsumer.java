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

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;

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
        try {
            ImportMessage msg = objectMapper.readValue(jsonMessage, ImportMessage.class);
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
                        LocalDate data  = row.getCell(2).getLocalDateTimeCellValue().toLocalDate();
                        BigDecimal ore  = BigDecimal.valueOf(row.getCell(3).getNumericCellValue());

                        // Lookup: il dipendente e il sito devono esistere
                        Dipendente dip = dipendenteRepo.perCodiceFiscale(cf);
                        Sito sito = sitoRepo.perNome(nomeSito);

                        if (dip == null || sito == null) {
                            LOG.warnf("Riga %d ignorata: dipendente o sito non trovato (cf=%s, sito=%s)",
                                    i, cf, nomeSito);
                            righeErrore++;
                            continue;
                        }

                        Timesheet ts = new Timesheet();
                        ts.dipendente = dip;
                        ts.sito = sito;
                        ts.dataLavoro = data;
                        ts.oreLavorate = ore;
                        timesheetRepo.persist(ts);
                        righeOk++;

                    } catch (Exception e) {
                        LOG.warnf("Errore sulla riga %d: %s", i, e.getMessage());
                        righeErrore++;
                    }
                }
            }

            LOG.infof("Import completato (%s): %d righe inserite, %d errori",
                    msg.fileName, righeOk, righeErrore);

            // Pulizia: rimuove il file temporaneo dopo l'elaborazione
            Files.deleteIfExists(Paths.get(msg.filePath));

        } catch (Exception e) {
            LOG.errorf(e, "Errore fatale nell'elaborazione del messaggio di import");
        }
    }

    // Helper: legge una cella come stringa in modo sicuro
    private String getString(Cell cell) {
        if (cell == null) return null;
        return cell.getStringCellValue().trim();
    }
}