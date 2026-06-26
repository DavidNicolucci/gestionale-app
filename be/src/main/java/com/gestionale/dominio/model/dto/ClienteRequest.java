package com.gestionale.dominio.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ClienteRequest {

    @NotBlank(message = "La ragione sociale è obbligatoria")
    public String ragioneSociale;

    @Size(max = 20, message = "La partita IVA non può superare 20 caratteri")
    public String partitaIva;     // opzionale

    public String indirizzo;      // opzionale
}