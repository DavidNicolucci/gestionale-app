package com.gestionale.dominio.model.dto;

import com.gestionale.dominio.model.entity.Cliente;

public class ClienteResponse {
    public Long id;
    public String ragioneSociale;
    public String partitaIva;
    public String indirizzo;
    public boolean eliminato;      // il frontend lo mostra in grigio e offre "ripristina"

    public static ClienteResponse da(Cliente c) {
        ClienteResponse r = new ClienteResponse();
        r.id = c.id;
        r.ragioneSociale = c.ragioneSociale;
        r.partitaIva = c.partitaIva;
        r.indirizzo = c.indirizzo;
        r.eliminato = c.eliminato;
        return r;
    }
}