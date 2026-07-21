package com.gestionale.dominio.service;

import com.gestionale.dominio.model.dto.ClienteRequest;
import com.gestionale.dominio.model.entity.Cliente;
import com.gestionale.dominio.repository.ClienteRepository;
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

    // findByIdOptional().orElseThrow(): il "non trovato" e' gestito dalla firma stessa,
    // non c'e' nessun null da controllare e nessun NullPointerException possibile.
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

    @Transactional
    public Cliente aggiorna(Long id, ClienteRequest req) {
        Cliente c = trovaPerId(id);
        c.ragioneSociale = req.ragioneSociale;
        c.partitaIva = req.partitaIva;
        c.indirizzo = req.indirizzo;
        LOG.infof("Cliente aggiornato: id=%d", id);
        return c;   // dirty checking: l'UPDATE parte al commit
    }

    @Transactional
    public void elimina(Long id) {
        boolean rimosso = repository.deleteById(id);
        if (!rimosso) {
            throw new NotFoundException("Cliente " + id + " non trovato");
        }
        // ATTENZIONE: sito ha ON DELETE CASCADE sulla FK cliente_id -> eliminando un
        // cliente spariscono anche tutti i suoi siti.
        LOG.infof("Cliente eliminato: id=%d (con i siti collegati, per il CASCADE)", id);
    }
}
