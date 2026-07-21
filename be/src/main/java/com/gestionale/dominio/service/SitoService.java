package com.gestionale.dominio.service;

import com.gestionale.dominio.model.entity.Cliente;
import com.gestionale.dominio.model.entity.Sito;
import com.gestionale.dominio.repository.ClienteRepository;
import com.gestionale.dominio.repository.SitoRepository;
import com.gestionale.dominio.model.dto.SitoRequest;
import com.gestionale.dominio.model.dto.SitoResponse;
import com.gestionale.dominio.model.dto.SitoRicercaRequest;
import com.gestionale.dominio.model.dto.PaginaResponse;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
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
    private final ClienteRepository clienteRepository;   // per caricare il cliente dal suo id

    @Inject
    public SitoService(SitoRepository repository, ClienteRepository clienteRepository) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
    }

    public List<Sito> listaTutti() {
        return repository.listAll();
    }

    // Ricerca paginata per la home. Serve @Transactional perche' SitoResponse legge
    // "sito.cliente", che Hibernate carica solo quando glielo chiedi: senza transazione
    // aperta darebbe errore.
    @Transactional
    public PaginaResponse<SitoResponse> cerca(SitoRicercaRequest req) {
        PanacheQuery<Sito> query = repository
                .cerca(req, req.sort())
                .page(req.pagePanache());

        List<SitoResponse> risultati = query.list().stream()
                .map(SitoResponse::da)
                .toList();

        return PaginaResponse.di(risultati, query.pageCount(), req.pagina(), query.count());
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
        s.cliente = caricaCliente(req.clienteId);
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
        // Niente persist: Hibernate vede le modifiche e fa l'UPDATE a fine transazione.
        return s;
    }

    @Transactional
    public void elimina(Long id) {
        boolean rimosso = repository.deleteById(id);
        if (!rimosso) {
            throw new NotFoundException("Sito " + id + " non trovato");
        }
        LOG.infof("Sito eliminato: id=%d", id);
    }

    // Il cliente indicato potrebbe non esistere: e' un errore di chi chiama, quindi 404.
    // Metodo unico usato da crea e aggiorna, per non ripetere il codice.
    private Cliente caricaCliente(Long clienteId) {
        return clienteRepository.findByIdOptional(clienteId)
                .orElseThrow(() -> new NotFoundException("Cliente " + clienteId + " non trovato"));
    }
}
