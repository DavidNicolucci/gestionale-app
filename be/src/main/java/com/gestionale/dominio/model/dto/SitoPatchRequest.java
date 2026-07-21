package com.gestionale.dominio.model.dto;

import jakarta.validation.constraints.Pattern;

// Campi per la modifica di un sito (PATCH): si mandano solo quelli da cambiare.
// Un campo che non arriva resta com'e'. L'id sta nell'indirizzo, non qui.
public class SitoPatchRequest {

    // Se lo mandi deve avere del testo: il nome non puo' restare vuoto.
    @Pattern(regexp = ".*\\S.*", message = "Il nome del sito non può essere vuoto")
    public String nome;

    public String indirizzo;

    // Serve per spostare il sito su un altro cliente. Se non arriva, resta quello di prima.
    public Long clienteId;
}
