package com.gestionale.dominio.service;

import com.gestionale.dominio.model.dto.ClienteRequest;
import com.gestionale.dominio.model.entity.Cliente;
import com.gestionale.dominio.repository.ClienteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.List;

@ApplicationScoped
public class ClienteService {

    @Inject
    ClienteRepository repository;

    public List<Cliente> listaTutti() {
        return repository.listAll();
    }

    public Cliente trovaPerId(Long id) {
        Cliente c = repository.findById(id);
        if (c == null) {
            throw new NotFoundException("Cliente " + id + " non trovato");
        }
        return c;
    }

    @Transactional
    public Cliente crea(ClienteRequest req) {
        Cliente c = new Cliente();
        c.ragioneSociale = req.ragioneSociale;
        c.partitaIva = req.partitaIva;
        c.indirizzo = req.indirizzo;
        repository.persist(c);
        return c;
    }

    @Transactional
    public Cliente aggiorna(Long id, ClienteRequest req) {
        Cliente c = trovaPerId(id);
        c.ragioneSociale = req.ragioneSociale;
        c.partitaIva = req.partitaIva;
        c.indirizzo = req.indirizzo;
        return c;   // dirty checking: l'UPDATE parte al commit
    }

    @Transactional
    public void elimina(Long id) {
        boolean rimosso = repository.deleteById(id);
        if (!rimosso) {
            throw new NotFoundException("Cliente " + id + " non trovato");
        }
    }
}