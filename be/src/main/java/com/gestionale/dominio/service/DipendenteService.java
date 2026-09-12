package com.gestionale.dominio.service;

import com.gestionale.dominio.model.dto.DipendentePatchRequest;
import com.gestionale.dominio.model.dto.DipendenteRequest;
import com.gestionale.dominio.model.dto.DipendenteResponse;
import com.gestionale.dominio.model.dto.DipendenteRicercaRequest;
import com.gestionale.dominio.model.dto.PaginaResponse;
import com.gestionale.dominio.model.dto.RinnovoRequest;
import com.gestionale.dominio.model.dto.ScadenzeResponse;
import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.model.enums.FiltroStato;
import com.gestionale.dominio.model.enums.StatoDipendente;
import com.gestionale.dominio.repository.DipendenteRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CacheKey;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DipendenteService {

    private static final Logger LOG = Logger.getLogger(DipendenteService.class);

    // I nomi delle cache in costanti: se li scrivessimo a mano, un errore di battitura
    // non lo segnalerebbe il compilatore e ci ritroveremmo dati vecchi senza capire perche'.
    //
    // La costante pero' copre solo le annotazioni qui sotto. Gli stessi due nomi vanno
    // ripetuti a mano in application.properties (scadenza e metriche), e li' il
    // compilatore non arriva: un nome sbagliato configura in silenzio una cache che non
    // esiste, senza nessun errore. E' successo davvero - c'era rimasto "nome-cache",
    // il segnaposto della documentazione - e le due cache vere sono state senza
    // metriche finche' non se n'e' accorto qualcuno leggendo il file.
    //
    // Da sapere se un giorno il backend girera' su piu' di una macchina: la cache e'
    // per processo, quindi @CacheInvalidate svuota solo quella dell'istanza che ha
    // ricevuto la modifica. Un dipendente aggiornato sull'istanza A resterebbe vecchio
    // sulla B fino alla scadenza. E' lo stesso limite di ProtezioneLogin e ha la stessa
    // soluzione (una cache condivisa, tipo Redis); qui lo rende tollerabile proprio la
    // scadenza corta di dieci minuti, che c'e' gia' per un'altra ragione.
    private static final String CACHE_LISTA = "dipendenti-lista";
    private static final String CACHE_SINGOLO = "dipendente-singolo";

    // Repository passato nel costruttore e final: e' obbligatorio e non cambia mai.
    private final DipendenteRepository repository;

    @Inject
    public DipendenteService(DipendenteRepository repository) {
        this.repository = repository;
    }

    // ---- Letture ----

    // In cache mettiamo i DTO e non le entity: un'entity tenuta da parte perde il
    // collegamento col database e leggerne i campi collegati darebbe errore.
    // Gli eliminati restano fuori: chi chiede "l'elenco dei dipendenti" non li vuole.
    @CacheResult(cacheName = CACHE_LISTA)
    public List<DipendenteResponse> listaTutti() {
        return repository.elenco(FiltroStato.ESCLUDI_ELIMINATI)
                .list()
                .stream()
                .map(DipendenteResponse::da)
                .toList();
    }

    // Questa NON la mettiamo in cache: filtri e pagina cambiano a ogni chiamata, quindi
    // la cache non servirebbe a niente e leggiamo sempre dati aggiornati.
    public PaginaResponse<DipendenteResponse> cerca(DipendenteRicercaRequest req) {
        PanacheQuery<Dipendente> query = repository
                .cerca(req, req.sort())
                .page(req.pagePanache());

        List<DipendenteResponse> risultati = query.list().stream()
                .map(DipendenteResponse::da)
                .toList();

        return PaginaResponse.di(risultati, query.pageCount(), req.pagina(), query.count());
    }

    // Il dettaglio risponde anche su scaduti ed eliminati. Nascondere un elenco e' utile,
    // nascondere la scheda di qualcuno che esiste vuol dire non poter piu' sapere perche'
    // e' stato eliminato o quando e' scaduto. Lo stato ce l'ha dentro la risposta.
    @CacheResult(cacheName = CACHE_SINGOLO)   // la chiave della cache e' l'id
    public DipendenteResponse trovaPerId(Long id) {
        return DipendenteResponse.da(caricaEntity(id));
    }

    // Quanti contratti scadono da oggi entro la finestra di preavviso: e' il numero che
    // il frontend mostra nell'avviso sopra la tabella. Non lo mettiamo in cache: e' una
    // sola COUNT, e un avviso che resta indietro di dieci minuti sulle scadenze e'
    // proprio quello che l'avviso dovrebbe evitare.
    public ScadenzeResponse scadenze() {
        long quanti = repository
                .inScadenza(LocalDate.now().plusDays(Dipendente.GIORNI_PREAVVISO))
                .count();
        return ScadenzeResponse.di(quanti);
    }

    // ---- Scritture ----

    // @CacheInvalidateAll anche su CACHE_SINGOLO: quando la creazione finisce per
    // riattivare un eliminato tocca una riga che gia' esiste, e quindi un id che
    // potrebbe essere in cache. Non avendo qui l'id fra i parametri non possiamo
    // invalidare la singola voce, e svuotare tutto e' preferibile a rispondere
    // "eliminato" su uno appena tornato in servizio.
    @Transactional
    @CacheInvalidateAll(cacheName = CACHE_LISTA)
    @CacheInvalidateAll(cacheName = CACHE_SINGOLO)
    public DipendenteResponse crea(DipendenteRequest req) {
        // Il codice fiscale e' unico sul database. Cerchiamo su TUTTI, eliminati
        // compresi: e' l'unico modo di distinguere "esiste gia'" da "c'era e l'avevamo
        // eliminato", che sono due situazioni diverse.
        Optional<Dipendente> esistente =
                repository.perCodiceFiscale(req.codiceFiscale, FiltroStato.TUTTI);

        if (esistente.isPresent()) {
            Dipendente d = esistente.get();

            // Uno in servizio non si duplica. Il vincolo c'e' anche sul database: se due
            // richieste arrivano insieme il controllo puo' passare per entrambe, e allora
            // blocca il database e rispondiamo comunque 409.
            if (!d.eliminato) {
                throw new WebApplicationException(
                        "Esiste già un dipendente con codice fiscale " + req.codiceFiscale, 409);
            }

            // Era eliminato: la stessa persona torna sulla stessa riga, con i dati nuovi.
            // Creare una riga in piu' vorrebbe dire due codici fiscali uguali (il vincolo
            // UNIQUE non lo permette) e, soprattutto, i suoi timesheet vecchi spalmati su
            // due dipendenti diversi.
            d.eliminato = false;
            copiaCampi(req, d);
            LOG.infof("Dipendente riattivato al posto di crearlo: id=%d, cf=%s", d.id, d.codiceFiscale);
            return DipendenteResponse.da(d);
        }

        Dipendente d = new Dipendente();
        copiaCampi(req, d);

        repository.persist(d);
        return DipendenteResponse.da(d);
    }

    // @CacheKey serve a dire che la chiave e' solo l'id: senza, sarebbe la coppia
    // (id, req) e non troverebbe mai la voce salvata da trovaPerId, quindi non
    // cancellerebbe niente dalla cache.
    @Transactional
    @CacheInvalidateAll(cacheName = CACHE_LISTA)      // la lista cambia
    @CacheInvalidate(cacheName = CACHE_SINGOLO)       // e anche questo dipendente
    public DipendenteResponse aggiorna(@CacheKey Long id, DipendentePatchRequest req) {
        Dipendente d = caricaEntity(id);  // 404 se non esiste
        vietaSeNonModificabile(d);
        applicaPatch(req, d);
        // Niente persist: l'oggetto arriva dal database, Hibernate vede le modifiche
        // e fa l'UPDATE da solo alla fine della transazione.
        return DipendenteResponse.da(d);
    }

    // Cancellazione logica: la riga resta. I timesheet puntano al dipendente, e una
    // cancellazione fisica si porterebbe dietro ore gia' consuntivate, cioe' numeri
    // che qualcuno ha gia' fatturato.
    @Transactional
    @CacheInvalidateAll(cacheName = CACHE_LISTA)
    @CacheInvalidate(cacheName = CACHE_SINGOLO)
    public void elimina(@CacheKey Long id) {
        Dipendente d = caricaEntity(id);

        // Gia' eliminato: non e' un errore, il risultato voluto c'e' gia'. Ripetere la
        // stessa DELETE non deve dare esiti diversi dalla prima.
        if (d.eliminato) {
            LOG.infof("Dipendente %d era gia' eliminato: niente da fare", id);
            return;
        }

        d.eliminato = true;
        LOG.infof("Dipendente eliminato logicamente: id=%d", id);
    }

    // Rimette in anagrafica un eliminato. Non tocca il contratto: se era anche scaduto,
    // dopo il ripristino risulta SCADUTO e per usarlo serve anche un rinnovo. Sono due
    // fatti distinti e restano due gesti distinti.
    @Transactional
    @CacheInvalidateAll(cacheName = CACHE_LISTA)
    @CacheInvalidate(cacheName = CACHE_SINGOLO)
    public DipendenteResponse ripristina(@CacheKey Long id) {
        Dipendente d = caricaEntity(id);

        if (!d.eliminato) {
            throw new WebApplicationException(
                    "Il dipendente " + id + " non è eliminato: non c'è niente da ripristinare", 409);
        }

        d.eliminato = false;
        LOG.infof("Dipendente ripristinato: id=%d", id);
        return DipendenteResponse.da(d);
    }

    // Sposta in avanti la scadenza del contratto. Si puo' rinnovare in anticipo (il caso
    // normale: il contratto scade fra una settimana e lo si prolunga) oppure dopo che e'
    // scaduto, ed e' allora l'unica strada per riportarlo in servizio.
    @Transactional
    @CacheInvalidateAll(cacheName = CACHE_LISTA)
    @CacheInvalidate(cacheName = CACHE_SINGOLO)
    public DipendenteResponse rinnova(@CacheKey Long id, RinnovoRequest req) {
        Dipendente d = caricaEntity(id);

        // Un eliminato non si rinnova: prima torna in anagrafica, poi gli si guarda il
        // contratto. Rinnovarlo di nascosto lo farebbe riapparire senza che nessuno
        // abbia deciso di riprenderlo.
        if (d.eliminato) {
            throw new WebApplicationException(
                    "Il dipendente " + id + " è eliminato: ripristinalo prima di rinnovargli il contratto", 409);
        }

        // Senza data di scadenza il contratto e' a tempo indeterminato: non scade, quindi
        // non c'e' niente da rinnovare. Se davvero lo si vuole portare a termine, e' una
        // modifica del contratto e passa dalla PATCH.
        if (d.dataScadenza == null) {
            throw new WebApplicationException(
                    "Il dipendente " + id + " ha un contratto a tempo indeterminato: non scade", 409);
        }

        LOG.infof("Contratto rinnovato: id=%d, da %s a %s", id, d.dataScadenza, req.dataScadenza);
        d.dataScadenza = req.dataScadenza;
        if (req.tipoContratto != null) {
            d.tipoContratto = req.tipoContratto;
        }
        return DipendenteResponse.da(d);
    }

    // ---- Interni ----

    // Se non c'e' lancia il 404 subito, cosi' nessuno deve controllare il null.
    // Qui la cache non la mettiamo: serve l'oggetto vero collegato al database,
    // quello che permette ad aggiorna() di salvare le modifiche.
    private Dipendente caricaEntity(Long id) {
        return repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Dipendente " + id + " non trovato"));
    }

    // Scaduti ed eliminati sono in sola lettura: per tornare modificabili devono passare
    // dall'operazione che li rimette in servizio. Cosi' riportare in vita un dipendente
    // e' sempre un gesto dichiarato, e non l'effetto collaterale di una PATCH qualsiasi
    // mandata per correggere un cognome.
    private void vietaSeNonModificabile(Dipendente d) {
        StatoDipendente stato = d.stato(LocalDate.now());

        if (stato == StatoDipendente.ELIMINATO) {
            throw new WebApplicationException(
                    "Il dipendente " + d.id + " è eliminato: usa il ripristino prima di modificarlo", 409);
        }
        if (stato == StatoDipendente.SCADUTO) {
            throw new WebApplicationException(
                    "Il contratto del dipendente " + d.id + " è scaduto il " + d.dataScadenza
                            + ": usa il rinnovo prima di modificarlo", 409);
        }
    }

    private void copiaCampi(DipendenteRequest req, Dipendente d) {
        d.nome = req.nome;
        d.cognome = req.cognome;
        d.codiceFiscale = req.codiceFiscale;
        d.dataNascita = req.dataNascita;
        d.nazionalita = req.nazionalita;
        d.tipoContratto = req.tipoContratto;
        d.dataAssunzione = req.dataAssunzione;
        d.dataScadenza = req.dataScadenza;
    }

    // Modifica parziale: copia solo i campi arrivati nella richiesta.
    // Un campo null vuol dire "non toccarlo", quindi lo saltiamo.
    private void applicaPatch(DipendentePatchRequest req, Dipendente d) {
        if (req.nome != null) {
            d.nome = req.nome;
        }
        if (req.cognome != null) {
            d.cognome = req.cognome;
        }
        if (req.codiceFiscale != null) {
            // Come in crea(), il codice fiscale deve restare unico. Qui pero' escludiamo
            // il dipendente che stiamo modificando: senza "id <> ?2" troverebbe se stesso
            // e darebbe 409 anche a chi rimanda lo stesso codice fiscale senza cambiarlo.
            // Il conteggio comprende gli eliminati: la loro riga occupa il codice fiscale
            // come tutte le altre, ed e' il database a dirlo.
            if (repository.count("codiceFiscale = ?1 and id <> ?2", req.codiceFiscale, d.id) > 0) {
                throw new WebApplicationException(
                        "Esiste già un dipendente con codice fiscale " + req.codiceFiscale, 409);
            }
            d.codiceFiscale = req.codiceFiscale;
        }
        if (req.dataNascita != null) {
            d.dataNascita = req.dataNascita;
        }
        if (req.nazionalita != null) {
            d.nazionalita = req.nazionalita;
        }
        if (req.tipoContratto != null) {
            d.tipoContratto = req.tipoContratto;
        }
        if (req.dataAssunzione != null) {
            d.dataAssunzione = req.dataAssunzione;
        }
        // L'ordine conta: prima si assegna la data eventualmente arrivata, poi si guarda
        // se va tolta. Cosi' una richiesta contraddittoria (una data nuova insieme a
        // rimuoviScadenza) finisce nello stato piu' esplicito dei due, il contratto senza
        // termine, invece di dipendere da quale if viene scritto prima.
        if (req.dataScadenza != null) {
            d.dataScadenza = req.dataScadenza;
        }
        if (req.rimuoviScadenza) {
            d.dataScadenza = null;
            LOG.infof("Contratto portato a tempo indeterminato: id=%d", d.id);
        }
    }
}
