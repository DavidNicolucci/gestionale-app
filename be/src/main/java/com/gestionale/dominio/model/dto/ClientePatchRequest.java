package com.gestionale.dominio.model.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Campi per la modifica di un cliente (PATCH): si mandano solo quelli da cambiare.
// Un campo che non arriva resta com'e', quindi qui niente campi obbligatori.
// L'id non sta qui: e' gia' nell'indirizzo della chiamata (/api/clienti/{id}).
public class ClientePatchRequest {

    // Se la mandi deve avere del testo: la ragione sociale non puo' restare vuota.
    // La regex vuole almeno un carattere che non sia uno spazio.
    // I controlli tipo @Pattern e @Size non scattano sui campi null, quindi chi non
    // manda il campo non deve preoccuparsene.
    @Pattern(regexp = ".*\\S.*", message = "La ragione sociale non può essere vuota")
    public String ragioneSociale;

    @Size(max = 20, message = "La partita IVA non può superare 20 caratteri")
    public String partitaIva;

    public String indirizzo;
}
