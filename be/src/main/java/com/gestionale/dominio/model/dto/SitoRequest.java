package com.gestionale.dominio.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SitoRequest {

    @NotBlank(message = "Il nome del sito è obbligatorio")
    public String nome;

    public String indirizzo;     // opzionale

    @NotNull(message = "Il cliente di riferimento è obbligatorio")
    public Long clienteId;       // riferimento al cliente per ID
}