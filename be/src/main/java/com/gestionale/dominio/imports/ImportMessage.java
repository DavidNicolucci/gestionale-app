package com.gestionale.dominio.imports;

/**
 * Quello che viaggia in coda: non il file, solo il necessario per ritrovarlo.
 *
 * jobId e' il collegamento alla riga di import_job, cioe' al registro che sopravvive
 * al messaggio. E' quello che permette al consumer di scrivere da qualche parte com'e'
 * andata, e all'endpoint admin di rimettere in coda lo stesso import.
 */
public class ImportMessage {
    public String filePath;     // dove abbiamo salvato il file su disco
    public String fileName;     // nome originale, utile per i log
    public Long jobId;          // riga di import_job che segue questo file

    public ImportMessage() { }  // costruttore vuoto: serve per rileggere il JSON dalla coda

    public ImportMessage(String filePath, String fileName, Long jobId) {
        this.filePath = filePath;
        this.fileName = fileName;
        this.jobId = jobId;
    }
}
