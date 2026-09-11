package com.gestionale.dominio.model.dto;

import java.util.Set;

// Filtri per la ricerca dei siti: se un campo e' null non filtra.
public class SitoRicercaRequest extends RicercaPaginataRequest {
    public String nome;
    public Long clienteId;   // per vedere solo i siti di un cliente

    // Spunta "mostra eliminati" della barra filtri. Di default false: gli eliminati
    // stanno fuori da tutto finche' non li si chiede espressamente.
    // Primitivo e non Boolean: se il client non manda il campo, Jackson lascia false,
    // che e' gia' il comportamento giusto.
    public boolean includiEliminati;

    // Campi su cui si puo' ordinare. Se ne arriva un altro, risposta 400.
    private static final Set<String> CAMPI_ORDINABILI = Set.of("id", "nome", "indirizzo");

    @Override
    protected Set<String> campiOrdinabili() {
        return CAMPI_ORDINABILI;
    }
}
