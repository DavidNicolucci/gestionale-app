package com.gestionale.dominio.service;

import com.gestionale.dominio.model.dto.ClientePatchRequest;
import com.gestionale.dominio.model.dto.ClienteRequest;
import com.gestionale.dominio.model.dto.ClienteResponse;
import com.gestionale.dominio.model.dto.ClienteRicercaRequest;
import com.gestionale.dominio.model.dto.PaginaResponse;
import com.gestionale.dominio.model.entity.Cliente;
import com.gestionale.dominio.repository.ClienteRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class ClienteService {

    private static final Logger LOG = Logger.getLogger(ClienteService.class);

    private final ClienteRepository repository;

    @Inject
    public ClienteService(ClienteRepository repository) {
        this.repository = repository;
    }

    public List<Cliente> listaTutti() {
        return repository.listAll();
    }

    // Ricerca paginata per la tabella della home. Partono due query, una per le righe
    // e una per il totale, ma nascono dalla stessa query di partenza: cosi' i filtri
    // sono per forza gli stessi e il totale non puo' essere sbagliato.
    public PaginaResponse<ClienteResponse> cerca(ClienteRicercaRequest req) {
        PanacheQuery<Cliente> query = repository
                .cerca(req, req.sort())
                .page(req.pagePanache());

        List<ClienteResponse> risultati = query.list().stream()
                .map(ClienteResponse::da)
                .toList();

        return PaginaResponse.di(risultati, query.pageCount(), req.pagina(), query.count());
    }

    // Se non c'e' lanciamo subito il 404, cosi' chi chiama non deve controllare il null.
    public Cliente trovaPerId(Long id) {
        return repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Cliente " + id + " non trovato"));
    }

    @Transactional
    public Cliente crea(ClienteRequest req) {
        Cliente c = new Cliente();
        c.ragioneSociale = req.ragioneSociale;
        c.partitaIva = req.partitaIva;
        c.indirizzo = req.indirizzo;
        repository.persist(c);
        LOG.infof("Cliente creato: id=%d, ragioneSociale=%s", c.id, c.ragioneSociale);
        return c;
    }

    // Modifica parziale: cambia solo i campi arrivati nella richiesta, gli altri
    // restano come sono. Un campo null vuol dire "non toccarlo".
    @Transactional
    public Cliente aggiorna(Long id, ClientePatchRequest req) {
        Cliente c = trovaPerId(id);
        if (req.ragioneSociale != null) {
            c.ragioneSociale = req.ragioneSociale;
        }
        if (req.partitaIva != null) {
            c.partitaIva = req.partitaIva;
        }
        if (req.indirizzo != null) {
            c.indirizzo = req.indirizzo;
        }
        LOG.infof("Cliente aggiornato: id=%d", id);
        // Niente persist: l'oggetto arriva dal database, Hibernate si accorge da solo
        // delle modifiche e fa l'UPDATE quando la transazione finisce.
        return c;
    }

    @Transactional
    public void elimina(Long id) {
        boolean rimosso = repository.deleteById(id);
        if (!rimosso) {
            throw new NotFoundException("Cliente " + id + " non trovato");
        }
        // ATTENZIONE: cancellando un cliente il database cancella anche tutti i suoi siti.
        LOG.infof("Cliente eliminato: id=%d (con i siti collegati)", id);
    }
}
