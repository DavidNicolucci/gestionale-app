package com.gestionale.dominio.service;

import com.gestionale.dominio.model.entity.Cliente;
import com.gestionale.dominio.model.entity.Sito;
import com.gestionale.dominio.model.enums.FiltroStato;
import com.gestionale.dominio.repository.ClienteRepository;
import com.gestionale.dominio.repository.SitoRepository;
import com.gestionale.dominio.repository.TimesheetRepository;
import com.gestionale.dominio.model.dto.SitoPatchRequest;
import com.gestionale.dominio.model.dto.SitoRequest;
import com.gestionale.dominio.model.dto.SitoResponse;
import com.gestionale.dominio.model.dto.SitoRicercaRequest;
import com.gestionale.dominio.model.dto.PaginaResponse;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class SitoService {

    private static final Logger LOG = Logger.getLogger(SitoService.class);

    private final SitoRepository repository;
    private final ClienteRepository clienteRepository;   // per caricare il cliente dal suo id
    private final TimesheetRepository timesheetRepository;

    @Inject
    public SitoService(SitoRepository repository,
                       ClienteRepository clienteRepository,
                       TimesheetRepository timesheetRepository) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.timesheetRepository = timesheetRepository;
    }

    // Elenco per le tendine di scelta: sui siti eliminati non si registrano ore, quindi
    // non ci sono.
    public List<Sito> listaTutti() {
        return repository.elenco(FiltroStato.ESCLUDI_ELIMINATI).list();
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

    // Risponde anche sugli eliminati, per gli stessi motivi del cliente: la scheda
    // serve a ripristinarli e a capire dove sono state fatte le ore vecchie.
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

    // Modifica parziale: cambia solo i campi arrivati nella richiesta, gli altri
    // restano come sono. Un campo null vuol dire "non toccarlo".
    @Transactional
    public Sito aggiorna(Long id, SitoPatchRequest req) {
        Sito s = trovaPerId(id);

        if (s.eliminato) {
            throw new WebApplicationException(
                    "Il sito " + id + " è eliminato: ripristinalo prima di modificarlo", 409);
        }

        if (req.nome != null) {
            s.nome = req.nome;
        }
        if (req.indirizzo != null) {
            s.indirizzo = req.indirizzo;
        }
        if (req.clienteId != null) {
            s.cliente = caricaCliente(req.clienteId);
        }
        LOG.infof("Sito aggiornato: id=%d", id);
        // Niente persist: Hibernate vede le modifiche e fa l'UPDATE a fine transazione.
        return s;
    }

    // Cancellazione logica. Il sito esce dalle tendine e non accetta piu' ore nuove,
    // ma le sue righe di timesheet restano dove sono e continuano a fare totale: quelle
    // ore sono state lavorate davvero, di solito sono gia' state fatturate, e il fatto
    // che il cantiere abbia chiuso non le cancella.
    //
    // Qui non serve nessun controllo sui timesheet collegati: e' proprio il caso normale
    // che ce ne siano, ed e' la ragione per cui la riga non si tocca piu'.
    @Transactional
    public void elimina(Long id) {
        Sito s = trovaPerId(id);

        if (s.eliminato) {
            LOG.infof("Sito %d era gia' eliminato: niente da fare", id);
            return;
        }

        s.eliminato = true;
        LOG.infof("Sito eliminato logicamente: id=%d, nome=%s (%d righe di ore conservate)",
                id, s.nome, timesheetRepository.contaPerSito(id));
    }

    // Rimette in elenco un sito eliminato.
    @Transactional
    public Sito ripristina(Long id) {
        Sito s = trovaPerId(id);

        if (!s.eliminato) {
            throw new WebApplicationException(
                    "Il sito " + id + " non è eliminato: non c'è niente da ripristinare", 409);
        }

        // Un sito attivo appeso a un cliente eliminato non deve esistere: e' lo stesso
        // stato che l'eliminazione del cliente si rifiuta di creare. L'ordine giusto
        // e' cliente prima, sito poi.
        if (s.cliente.eliminato) {
            throw new WebApplicationException(
                    "Il sito " + id + " appartiene al cliente " + s.cliente.ragioneSociale
                            + ", che è eliminato: ripristina prima il cliente", 409);
        }

        s.eliminato = false;
        LOG.infof("Sito ripristinato: id=%d", id);
        return s;
    }

    // Il cliente indicato potrebbe non esistere: e' un errore di chi chiama, quindi 404.
    // Metodo unico usato da crea e aggiorna, per non ripetere il codice.
    //
    // Legge con il lucchetto condiviso sulla riga del cliente: cosi' non puo' capitare
    // che il cliente venga eliminato nell'istante fra questo controllo e il salvataggio
    // del sito. ClienteService.elimina prende il lucchetto esclusivo sulla stessa riga,
    // quindi una delle due transazioni aspetta l'altra invece di sovrapporsi.
    private Cliente caricaCliente(Long clienteId) {
        Cliente c = clienteRepository.perIdCondiviso(clienteId)
                .orElseThrow(() -> new NotFoundException("Cliente " + clienteId + " non trovato"));

        if (c.eliminato) {
            throw new WebApplicationException(
                    "Il cliente " + c.ragioneSociale + " è eliminato: non ci si possono collegare siti."
                            + " Ripristinalo, oppure scegli un altro cliente", 409);
        }
        return c;
    }
}
