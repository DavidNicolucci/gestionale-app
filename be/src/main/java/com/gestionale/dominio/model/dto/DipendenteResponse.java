package com.gestionale.dominio.model.dto;

import java.time.LocalDate;

public class DipendenteResponse {
    public Long id;
    public String nome;
    public String cognome;
    public String codiceFiscale;
    public LocalDate dataNascita;
    public String nazionalita;
    public String tipoContratto;
    public LocalDate dataAssunzione;
    public LocalDate dataScadenza;

    public static DipendenteResponse da(com.gestionale.dominio.model.entity.Dipendente d) {
        DipendenteResponse r = new DipendenteResponse();
        r.id = d.id;
        r.nome = d.nome;
        r.cognome = d.cognome;
        r.codiceFiscale = d.codiceFiscale;
        r.dataNascita = d.dataNascita;
        r.nazionalita = d.nazionalita;
        r.tipoContratto = d.tipoContratto;
        r.dataAssunzione = d.dataAssunzione;
        r.dataScadenza = d.dataScadenza;
        return r;
    }
}