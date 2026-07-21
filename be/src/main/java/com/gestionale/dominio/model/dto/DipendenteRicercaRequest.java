package com.gestionale.dominio.model.dto;

import java.util.Set;

// Filtri per la ricerca dei dipendenti: se un campo e' null non filtra.
public class DipendenteRicercaRequest extends RicercaPaginataRequest {
    public String nome;
    public String cognome;
    public String codiceFiscale;

    // Campi su cui si puo' ordinare. Se ne arriva un altro, risposta 400.
    private static final Set<String> CAMPI_ORDINABILI =
            Set.of("id", "nome", "cognome", "codiceFiscale", "dataNascita", "dataAssunzione", "dataScadenza");

    @Override
    protected Set<String> campiOrdinabili() {
        return CAMPI_ORDINABILI;
    }
}
