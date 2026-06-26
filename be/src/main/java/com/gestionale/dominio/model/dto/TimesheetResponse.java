package com.gestionale.dominio.model.dto;

import com.gestionale.dominio.model.entity.Timesheet;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TimesheetResponse {
    public Long id;
    public Long dipendenteId;
    public String dipendenteNominativo;     // "Mario Rossi" — comodo per il frontend
    public Long sitoId;
    public String sitoNome;
    public LocalDate dataLavoro;
    public BigDecimal oreLavorate;
    public String note;

    public static TimesheetResponse da(Timesheet t) {
        TimesheetResponse r = new TimesheetResponse();
        r.id = t.id;
        r.dipendenteId = t.dipendente.id;
        r.dipendenteNominativo = t.dipendente.nome + " " + t.dipendente.cognome;
        r.sitoId = t.sito.id;
        r.sitoNome = t.sito.nome;
        r.dataLavoro = t.dataLavoro;
        r.oreLavorate = t.oreLavorate;
        r.note = t.note;
        return r;
    }
}