package com.gestionale.dominio.imports;

/**
 * Com'e' andato un blocco di righe. I tre numeri si sommano a quelli del job.
 *
 * "aggiornate" e' la riga che esisteva gia' per quel dipendente, quel sito e quel
 * giorno: le ore vengono sovrascritte invece di aggiungere un doppione. E' il numero
 * da guardare dopo un rilancio, perche' e' quello che dice quanto lavoro era gia'
 * stato fatto dal tentativo precedente.
 */
public record EsitoBlocco(int inserite, int aggiornate, int scartate) {

    static final EsitoBlocco VUOTO = new EsitoBlocco(0, 0, 0);

    EsitoBlocco piu(EsitoBlocco altro) {
        return new EsitoBlocco(inserite + altro.inserite,
                aggiornate + altro.aggiornate,
                scartate + altro.scartate);
    }

    int totale() {
        return inserite + aggiornate + scartate;
    }
}
