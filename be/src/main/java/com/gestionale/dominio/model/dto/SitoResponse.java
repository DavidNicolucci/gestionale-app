package com.gestionale.dominio.model.dto;

import com.gestionale.dominio.model.entity.Sito;

public class SitoResponse {
    public Long id;
    public String nome;
    public String indirizzo;
    public Long clienteId;
    public String clienteRagioneSociale;   // comodo per il frontend: mostra il nome, non solo l'id

    public static SitoResponse da(Sito s) {
        SitoResponse r = new SitoResponse();
        r.id = s.id;
        r.nome = s.nome;
        r.indirizzo = s.indirizzo;
        r.clienteId = s.cliente.id;
        r.clienteRagioneSociale = s.cliente.ragioneSociale;
        return r;
    }
}