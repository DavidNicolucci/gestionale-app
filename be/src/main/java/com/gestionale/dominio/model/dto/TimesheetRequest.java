package com.gestionale.dominio.model.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TimesheetRequest {

    @NotNull(message = "Il dipendente è obbligatorio")
    public Long dipendenteId;

    @NotNull(message = "Il sito è obbligatorio")
    public Long sitoId;

    @NotNull(message = "La data di lavoro è obbligatoria")
    public LocalDate dataLavoro;

    @NotNull(message = "Le ore sono obbligatorie")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le ore devono essere positive")
    @DecimalMax(value = "24.0", message = "Le ore non possono superare 24 in un giorno")
    public BigDecimal oreLavorate;

    public String note;     // opzionale
}