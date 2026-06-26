package com.gestionale.dominio.model.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public class DipendenteRequest {

    @NotBlank(message = "Il nome è obbligatorio")
    public String nome;

    @NotBlank(message = "Il cognome è obbligatorio")
    public String cognome;

    @NotBlank
    @Size(min = 16, max = 16, message = "Il codice fiscale deve essere di 16 caratteri")
    public String codiceFiscale;

    @NotNull(message = "La data di nascita è obbligatoria")
    @Past(message = "La data di nascita deve essere nel passato")
    public LocalDate dataNascita;

    @NotBlank
    public String nazionalita;

    @NotBlank
    public String tipoContratto;

    @NotNull
    public LocalDate dataAssunzione;

    public LocalDate dataScadenza;     // opzionale (indeterminato)
}