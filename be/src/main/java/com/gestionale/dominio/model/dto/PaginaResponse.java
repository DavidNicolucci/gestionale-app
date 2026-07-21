package com.gestionale.dominio.model.dto;

import java.util.List;

// Risposta di una ricerca paginata. E' generica cosi' la usiamo per clienti,
// dipendenti e siti, e il frontend ha un solo modello da gestire.
public class PaginaResponse<T> {

    public List<T> risultati;     // gli elementi di questa pagina
    public int pagine;            // quante pagine ci sono in tutto
    public int numeroDiPagina;    // pagina corrente, si parte da 0
    public long totaleElementi;   // righe totali trovate dai filtri, non solo di questa pagina

    public static <T> PaginaResponse<T> di(List<T> risultati, int pagine, int numeroDiPagina, long totaleElementi) {
        PaginaResponse<T> p = new PaginaResponse<>();
        p.risultati = risultati;
        p.pagine = pagine;
        p.numeroDiPagina = numeroDiPagina;
        p.totaleElementi = totaleElementi;
        return p;
    }
}
