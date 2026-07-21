package com.gestionale.dominio.model.dto;

import java.util.Set;

// Filtri per la ricerca dei clienti: se un campo e' null non filtra.
public class ClienteRicercaRequest extends RicercaPaginataRequest {
    public String ragioneSociale;
    public String partitaIva;

    // Campi su cui si puo' ordinare. Se ne arriva un altro, risposta 400.
    private static final Set<String> CAMPI_ORDINABILI =
            Set.of("id", "ragioneSociale", "partitaIva", "indirizzo");

    @Override
    protected Set<String> campiOrdinabili() {
        return CAMPI_ORDINABILI;
    }
}
