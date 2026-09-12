package com.gestionale.dominio.imports;

import java.time.Instant;

/**
 * Un import come lo vede l'ADMIN nell'elenco.
 *
 * Rispetto alla riga sul database c'e' in piu' "ritentabile": e' la domanda che si fa
 * chi guarda l'elenco ("posso rilanciarlo?") e la risposta dipende da due cose insieme,
 * lo stato e la presenza del file su disco. Calcolarla qui evita che chi legge debba
 * incrociarle a mente.
 */
public class ImportJobResponse {

    public Long id;
    public String fileName;
    public StatoImport stato;
    public int tentativi;
    public int righeInserite;
    public int righeAggiornate;   // gia' presenti per quel dipendente/sito/giorno: ore corrette, non duplicate
    public int righeScartate;
    public int ultimaRiga;        // da qui riparte un rilancio che non sia "dall'inizio"
    public String errore;
    public String caricatoDa;
    public Instant creatoIl;
    public Instant aggiornatoIl;

    /** Il file e' ancora su disco: senza, non c'e' niente da rileggere. */
    public boolean fileDisponibile;

    /** Si puo' chiamare il rilancio senza forzarlo. */
    public boolean ritentabile;

    /** Percorso sul server: serve a chi deve andare a guardare il file di persona. */
    public String filePath;

    public static ImportJobResponse da(ImportJob j) {
        ImportJobResponse r = new ImportJobResponse();
        r.id = j.id;
        r.fileName = j.fileName;
        r.stato = j.stato;
        r.tentativi = j.tentativi;
        r.righeInserite = j.righeInserite;
        r.righeAggiornate = j.righeAggiornate;
        r.righeScartate = j.righeScartate;
        r.ultimaRiga = j.ultimaRiga;
        r.errore = j.errore;
        r.caricatoDa = j.caricatoDa;
        r.creatoIl = j.creatoIl;
        r.aggiornatoIl = j.aggiornatoIl;
        r.filePath = j.filePath;
        r.fileDisponibile = j.fileDisponibile();
        r.ritentabile = j.stato.ritentabile() && r.fileDisponibile;
        return r;
    }
}
