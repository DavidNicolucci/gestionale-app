package com.gestionale.dominio.service;

import com.gestionale.dominio.model.entity.Cliente;
import com.gestionale.dominio.model.entity.Sito;
import com.gestionale.dominio.repository.ClienteRepository;
import com.gestionale.dominio.repository.SitoRepository;
import com.gestionale.dominio.model.dto.SitoRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.List;

@ApplicationScoped
public class SitoService {

    @Inject
    SitoRepository repository;

    @Inject
    ClienteRepository clienteRepository;   // serve per risolvere il cliente dal suo id

    public List<Sito> listaTutti() {
        return repository.listAll();
    }

    public Sito trovaPerId(Long id) {
        Sito s = repository.findById(id);
        if (s == null) {
            throw new NotFoundException("Sito " + id + " non trovato");
        }
        return s;
    }

    @Transactional
    public Sito crea(SitoRequest req) {
        // Recupera il cliente di riferimento; se non esiste, è un errore del client
        Cliente cliente = clienteRepository.findById(req.clienteId);
        if (cliente == null) {
            throw new NotFoundException("Cliente " + req.clienteId + " non trovato");
        }

        Sito s = new Sito();
        s.nome = req.nome;
        s.indirizzo = req.indirizzo;
        s.cliente = cliente;          // collega la relazione ManyToOne
        repository.persist(s);
        return s;
    }

    @Transactional
    public Sito aggiorna(Long id, SitoRequest req) {
        Sito s = trovaPerId(id);
        Cliente cliente = clienteRepository.findById(req.clienteId);
        if (cliente == null) {
            throw new NotFoundException("Cliente " + req.clienteId + " non trovato");
        }
        s.nome = req.nome;
        s.indirizzo = req.indirizzo;
        s.cliente = cliente;
        return s;
    }

    @Transactional
    public void elimina(Long id) {
        boolean rimosso = repository.deleteById(id);
        if (!rimosso) {
            throw new NotFoundException("Sito " + id + " non trovato");
        }
    }
}