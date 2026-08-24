package com.gestionale.dominio.model.dto;

import com.gestionale.dominio.model.enums.StatoDipendente;

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

    // Non e' una colonna del database: si ricava da "eliminato" e dal confronto fra
    // la data di scadenza e oggi. Lo calcola il backend e non il frontend perche' e'
    // la stessa regola che decide chi si puo' usare: se la ricalcolasse anche il
    // client, prima o poi le due versioni direbbero cose diverse.
    public StatoDipendente stato;

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
        // "Oggi" lo leggiamo qui, al momento di rispondere. Attenzione alle cache dei
        // service: una risposta calcolata ieri e tenuta da parte direbbe ancora ATTIVO
        // su un contratto scaduto stanotte. Per questo le cache dei dipendenti hanno
        // una scadenza breve in application.properties.
        r.stato = d.stato(LocalDate.now());
        return r;
    }
}