package com.gestionale.dominio.model.dto;

import java.util.Set;

// Filtri per la ricerca dei dipendenti: se un campo e' null non filtra.
public class DipendenteRicercaRequest extends RicercaPaginataRequest {
    public String nome;
    public String cognome;
    public String codiceFiscale;

    // Spunta "mostra eliminati" della barra filtri. Di default false: gli eliminati
    // stanno fuori da tutto finche' non li si chiede espressamente. Gli scaduti invece
    // ci sono sempre, la tabella li mostra in grigio.
    // Primitivo e non Boolean: se il client non manda il campo, Jackson lascia false,
    // che e' gia' il comportamento giusto.
    public boolean includiEliminati;

    // Campi su cui si puo' ordinare. Se ne arriva un altro, risposta 400.
    private static final Set<String> CAMPI_ORDINABILI =
            Set.of("id", "nome", "cognome", "codiceFiscale", "dataNascita", "dataAssunzione", "dataScadenza");

    @Override
    protected Set<String> campiOrdinabili() {
        return CAMPI_ORDINABILI;
    }
}
