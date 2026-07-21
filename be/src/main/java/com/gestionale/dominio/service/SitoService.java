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
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class SitoService {

    private static final Logger LOG = Logger.getLogger(SitoService.class);

    private final SitoRepository repository;
    private final ClienteRepository clienteRepository;   // serve per risolvere il cliente dal suo id

    @Inject
    public SitoService(SitoRepository repository, ClienteRepository clienteRepository) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
    }

    public List<Sito> listaTutti() {
        return repository.listAll();
    }

    public Sito trovaPerId(Long id) {
        return repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Sito " + id + " non trovato"));
    }

    @Transactional
    public Sito crea(SitoRequest req) {
        Sito s = new Sito();
        s.nome = req.nome;
        s.indirizzo = req.indirizzo;
        s.cliente = caricaCliente(req.clienteId);   // collega la relazione ManyToOne
        repository.persist(s);
        LOG.infof("Sito creato: id=%d, nome=%s, clienteId=%d", s.id, s.nome, req.clienteId);
        return s;
    }

    @Transactional
    public Sito aggiorna(Long id, SitoRequest req) {
        Sito s = trovaPerId(id);
        s.nome = req.nome;
        s.indirizzo = req.indirizzo;
        s.cliente = caricaCliente(req.clienteId);
        LOG.infof("Sito aggiornato: id=%d", id);
        return s;   // dirty checking: UPDATE al commit
    }

    @Transactional
    public void elimina(Long id) {
        boolean rimosso = repository.deleteById(id);
        if (!rimosso) {
            throw new NotFoundException("Sito " + id + " non trovato");
        }
        LOG.infof("Sito eliminato: id=%d", id);
    }

    // Il cliente indicato dal client puo' non esistere: e' un errore della richiesta, non
    // un guasto. Un solo punto di lookup, riusato da crea() e aggiorna() (prima era duplicato).
    private Cliente caricaCliente(Long clienteId) {
        return clienteRepository.findByIdOptional(clienteId)
                .orElseThrow(() -> new NotFoundException("Cliente " + clienteId + " non trovato"));
    }
}
