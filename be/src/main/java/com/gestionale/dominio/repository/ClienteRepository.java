package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.dto.ClienteRicercaRequest;
import com.gestionale.dominio.model.entity.Cliente;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped     // cosi' Quarkus lo puo' iniettare nei service
public class ClienteRepository implements PanacheRepository<Cliente> {

    // persist, findById, listAll, delete e count ce li da' gia' Panache.
    // Qui scriviamo solo le query nostre.

    // Query di ricerca con i filtri passati: quelli vuoti li salta.
    // Non impagina: ci pensa il service, cosi' risultati e conteggio usano gli stessi filtri.
    public PanacheQuery<Cliente> cerca(ClienteRicercaRequest req, Sort sort) {
        FiltriPanache f = new FiltriPanache()
                .contiene("ragioneSociale", req.ragioneSociale)
                .contiene("partitaIva", req.partitaIva);

        return find(f.where(), sort, f.params());
    }
}