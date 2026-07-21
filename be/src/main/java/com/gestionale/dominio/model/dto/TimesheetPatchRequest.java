package com.gestionale.dominio.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;

// Campi per la modifica di una riga di timesheet (PATCH): si mandano solo quelli da
// cambiare. Un campo che non arriva resta com'e'. L'id sta nell'indirizzo, non qui.
public class TimesheetPatchRequest {

    // Servono per spostare le ore su un altro dipendente o su un altro sito.
    public Long dipendenteId;
    public Long sitoId;

    public LocalDate dataLavoro;

    // Questi due controlli non scattano se il campo non arriva.
    @DecimalMin(value = "0.0", inclusive = false, message = "Le ore devono essere positive")
    @DecimalMax(value = "24.0", message = "Le ore non possono superare 24 in un giorno")
    public BigDecimal oreLavorate;

    public String note;
}
