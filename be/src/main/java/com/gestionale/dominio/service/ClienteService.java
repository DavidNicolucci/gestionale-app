package com.gestionale.dominio.service;

import com.gestionale.dominio.model.dto.ClientePatchRequest;
import com.gestionale.dominio.model.dto.ClienteRequest;
import com.gestionale.dominio.model.dto.ClienteResponse;
import com.gestionale.dominio.model.dto.ClienteRicercaRequest;
import com.gestionale.dominio.model.dto.PaginaResponse;
import com.gestionale.dominio.model.entity.Cliente;
import com.gestionale.dominio.model.enums.FiltroStato;
import com.gestionale.dominio.repository.ClienteRepository;
import com.gestionale.dominio.repository.SitoRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class ClienteService {

    private static final Logger LOG = Logger.getLogger(ClienteService.class);

    // Quanti nomi di siti citare nel messaggio di errore prima di scrivere "e altri N".
    // Cinque bastano a far capire di quali si tratta; venti renderebbero il messaggio
    // illeggibile proprio nel momento in cui deve essere chiaro.
    private static final int MAX_SITI_NEL_MESSAGGIO = 5;

    private final ClienteRepository repository;
    private final SitoRepository sitoRepository;

    @Inject
    public ClienteService(ClienteRepository repository, SitoRepository sitoRepository) {
        this.repository = repository;
        this.sitoRepository = sitoRepository;
    }

    // Elenco per le tendine di scelta: gli eliminati non si possono scegliere, quindi
    // non ci sono.
    public List<Cliente> listaTutti() {
        return repository.elenco(FiltroStato.ESCLUDI_ELIMINATI).list();
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
    //
    // Risponde anche sugli eliminati: nascondere un elenco e' utile, nascondere la
    // scheda di un cliente che esiste vuol dire non poterlo piu' ripristinare ne'
    // sapere di chi erano le ore vecchie. Lo stato sta nella risposta.
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

        // Un eliminato non si modifica: prima torna in anagrafica. Senza questo
        // controllo si potrebbe cambiare la ragione sociale di un cliente che nessuno
        // vede piu', e ritrovarsela addosso al ripristino senza sapere da dove arriva.
        if (c.eliminato) {
            throw new WebApplicationException(
                    "Il cliente " + id + " è eliminato: ripristinalo prima di modificarlo", 409);
        }

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

    // Cancellazione logica. La riga resta: i siti del cliente puntano qui, e i timesheet
    // puntano ai siti. Una cancellazione fisica si porterebbe dietro ore gia' fatturate.
    //
    // Il cliente non si elimina finche' ha siti attivi. L'alternativa era propagare il
    // flag ai siti, ma poi al ripristino non si saprebbe piu' quali siti erano gia'
    // stati eliminati prima e quali sono caduti per via del cliente: si tornerebbe
    // indietro riaprendo cantieri chiusi da mesi. Meglio chiedere all'utente di
    // sistemare i siti, dicendogli quali sono.
    @Transactional
    public void elimina(Long id) {
        // Lettura con il lucchetto sulla riga, non la solita findById: da qui alla fine
        // della transazione nessun altro puo' toccare questo cliente. Serve contro la
        // corsa fra due utenti: senza, mentre noi contiamo zero siti attivi un altro
        // ne sta creando uno, e finiremmo con un sito vivo appeso a un cliente
        // eliminato. SitoService prende lo stesso lucchetto quando aggancia un sito.
        Cliente c = repository.perIdBloccato(id)
                .orElseThrow(() -> new NotFoundException("Cliente " + id + " non trovato"));

        // Gia' eliminato: non e' un errore, il risultato voluto c'e' gia'. Ripetere la
        // stessa DELETE non deve dare esiti diversi dalla prima.
        if (c.eliminato) {
            LOG.infof("Cliente %d era gia' eliminato: niente da fare", id);
            return;
        }

        long sitiAttivi = sitoRepository.contaAttiviPerCliente(id);
        if (sitiAttivi > 0) {
            throw new WebApplicationException(messaggioSitiAttivi(c, sitiAttivi), 409);
        }

        c.eliminato = true;
        LOG.infof("Cliente eliminato logicamente: id=%d, ragioneSociale=%s", id, c.ragioneSociale);
    }

    // Rimette in anagrafica un eliminato. Non tocca i suoi siti: quelli eliminati prima
    // restano eliminati, perche' erano stati chiusi per conto loro. Sono fatti distinti
    // e restano gesti distinti, come il ripristino e il rinnovo sul dipendente.
    @Transactional
    public Cliente ripristina(Long id) {
        Cliente c = trovaPerId(id);

        if (!c.eliminato) {
            throw new WebApplicationException(
                    "Il cliente " + id + " non è eliminato: non c'è niente da ripristinare", 409);
        }

        c.eliminato = false;
        LOG.infof("Cliente ripristinato: id=%d", id);
        return c;
    }

    // Il messaggio dice quanti sono e come si chiamano: "ha dei siti collegati"
    // lascerebbe l'utente a cercarli a mano fra tutti i siti in anagrafica.
    private String messaggioSitiAttivi(Cliente c, long quanti) {
        List<String> nomi = sitoRepository.nomiAttiviPerCliente(c.id, MAX_SITI_NEL_MESSAGGIO);

        String elenco = String.join(", ", nomi);
        long nonCitati = quanti - nomi.size();
        if (nonCitati > 0) {
            elenco += " e altri " + nonCitati;
        }

        return "Il cliente %s non può essere eliminato: ha ancora %d %s (%s). Elimina %s oppure spostal%s su un altro cliente, poi riprova."
                .formatted(
                        c.ragioneSociale,
                        quanti,
                        quanti == 1 ? "sito attivo" : "siti attivi",
                        elenco,
                        quanti == 1 ? "il sito" : "i siti",
                        quanti == 1 ? "o" : "i");
    }
}
