package com.gestionale.dominio.model.enums;

/**
 * Quali dipendenti deve tirare su una query.
 *
 * E' un enum e non una coppia di booleani perche' al punto di chiamata
 * "cercaTestuale(testo, 20, SOLO_ATTIVI)" si legge, mentre
 * "cercaTestuale(testo, 20, false, true)" no: bisogna andare a vedere la firma
 * per sapere quale dei due e' quale, e prima o poi si invertono.
 *
 * I tre valori sono in ordine di apertura crescente.
 */
public enum FiltroStato {

    /** Solo chi e' utilizzabile oggi: niente scaduti, niente eliminati.
     *  E' quello che serve alle tendine di scelta e all'assistente quando
     *  l'utente non chiede esplicitamente i cessati. */
    SOLO_ATTIVI,

    /** Attivi e scaduti, senza gli eliminati. E' quello che vuole la tabella:
     *  gli scaduti si vedono in grigio, sono storia dell'azienda. */
    ESCLUDI_ELIMINATI,

    /** Tutti, eliminati compresi. Solo su richiesta esplicita. */
    TUTTI
}
