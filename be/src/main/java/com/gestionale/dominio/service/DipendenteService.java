package com.gestionale.dominio.service;

import com.gestionale.dominio.model.dto.DipendentePatchRequest;
import com.gestionale.dominio.model.dto.DipendenteRequest;
import com.gestionale.dominio.model.dto.DipendenteResponse;
import com.gestionale.dominio.model.dto.DipendenteRicercaRequest;
import com.gestionale.dominio.model.dto.PaginaResponse;
import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.repository.DipendenteRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CacheKey;

@ApplicationScoped
public class DipendenteService {

    // I nomi delle cache in costanti: se li scrivessimo a mano, un errore di battitura
    // non lo segnalerebbe il compilatore e ci ritroveremmo dati vecchi senza capire perche'.
    private static final String CACHE_LISTA = "dipendenti-lista";
    private static final String CACHE_SINGOLO = "dipendente-singolo";

    // Repository passato nel costruttore e final: e' obbligatorio e non cambia mai.
    private final DipendenteRepository repository;

    @Inject
    public DipendenteService(DipendenteRepository repository) {
        this.repository = repository;
    }

    // In cache mettiamo i DTO e non le entity: un'entity tenuta da parte perde il
    // collegamento col database e leggerne i campi collegati darebbe errore.
    @CacheResult(cacheName = CACHE_LISTA)
    public List<DipendenteResponse> listaTutti() {
        return repository.listAll()
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

    @CacheResult(cacheName = CACHE_SINGOLO)   // la chiave della cache e' l'id
    public DipendenteResponse trovaPerId(Long id) {
        return DipendenteResponse.da(caricaEntity(id));
    }

    @Transactional                                    // o va tutto a buon fine, o non cambia niente
    @CacheInvalidateAll(cacheName = CACHE_LISTA)      // la lista in cache non va piu' bene
    public DipendenteResponse crea(DipendenteRequest req) {
        // Il codice fiscale deve essere unico. Lo controlliamo qui, ma il vincolo c'e'
        // anche sul database: se due richieste arrivano insieme il controllo puo' passare
        // per entrambe, e allora blocca il database e rispondiamo 409.
        if (repository.count("codiceFiscale", req.codiceFiscale) > 0) {
            throw new WebApplicationException(
                    "Esiste già un dipendente con codice fiscale " + req.codiceFiscale, 409);
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
        applicaPatch(req, d);
        // Niente persist: l'oggetto arriva dal database, Hibernate vede le modifiche
        // e fa l'UPDATE da solo alla fine della transazione.
        return DipendenteResponse.da(d);
    }

    @Transactional
    @CacheInvalidateAll(cacheName = CACHE_LISTA)
    @CacheInvalidate(cacheName = CACHE_SINGOLO)
    public void elimina(@CacheKey Long id) {
        boolean rimosso = repository.deleteById(id);
        if (!rimosso) {
            throw new NotFoundException("Dipendente " + id + " non trovato");
        }
    }

    // Se non c'e' lancia il 404 subito, cosi' nessuno deve controllare il null.
    // Qui la cache non la mettiamo: serve l'oggetto vero collegato al database,
    // quello che permette ad aggiorna() di salvare le modifiche.
    private Dipendente caricaEntity(Long id) {
        return repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Dipendente " + id + " non trovato"));
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
        if (req.dataScadenza != null) {
            d.dataScadenza = req.dataScadenza;
        }
    }
}
