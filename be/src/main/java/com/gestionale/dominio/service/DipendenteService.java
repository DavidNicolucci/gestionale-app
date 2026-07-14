package com.gestionale.dominio.service;

import com.gestionale.dominio.model.dto.DipendenteRequest;
import com.gestionale.dominio.model.dto.DipendenteResponse;
import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.repository.DipendenteRepository;
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

    // I nomi delle cache stanno in costanti: un refuso tra @CacheResult e @CacheInvalidate
    // non darebbe errore di compilazione, si tradurrebbe in dati stantii a runtime.
    private static final String CACHE_LISTA = "dipendenti-lista";
    private static final String CACHE_SINGOLO = "dipendente-singolo";

    @Inject
    DipendenteRepository repository;     // inject del repository (CDI)

    // In cache finiscono i DTO, NON le entity: un'entity messa in cache viene restituita
    // "detached" alle chiamate successive (persistence context ormai chiuso), e un eventuale
    // campo LAZY esploderebbe con LazyInitializationException. Il DTO e' un oggetto inerte.
    @CacheResult(cacheName = CACHE_LISTA)
    public List<DipendenteResponse> listaTutti() {
        return repository.listAll()      // metodo fornito da Panache
                .stream()
                .map(DipendenteResponse::da)
                .toList();
    }

    @CacheResult(cacheName = CACHE_SINGOLO)   // chiave = id (unico parametro)
    public DipendenteResponse trovaPerId(Long id) {
        return DipendenteResponse.da(caricaEntity(id));
    }

    @Transactional                                    // o tutto va a buon fine, o rollback
    @CacheInvalidateAll(cacheName = CACHE_LISTA)      // la lista non e' piu' valida
    public DipendenteResponse crea(DipendenteRequest req) {
        // Regola di business: CF univoco. Controllo applicativo + vincolo DB come rete di sicurezza.
        if (repository.count("codiceFiscale", req.codiceFiscale) > 0) {
            throw new WebApplicationException(
                    "Esiste già un dipendente con codice fiscale " + req.codiceFiscale, 409);
        }

        Dipendente d = new Dipendente();
        copiaCampi(req, d);

        repository.persist(d);           // INSERT
        return DipendenteResponse.da(d);
    }

    // @CacheKey su id: senza, la chiave sarebbe la coppia (id, req) e non combacerebbe mai
    // con quella usata da trovaPerId(id) -> l'invalidazione non colpirebbe nulla.
    @Transactional
    @CacheInvalidateAll(cacheName = CACHE_LISTA)      // la lista cambia
    @CacheInvalidate(cacheName = CACHE_SINGOLO)       // e anche il singolo aggiornato
    public DipendenteResponse aggiorna(@CacheKey Long id, DipendenteRequest req) {
        Dipendente d = caricaEntity(id);  // entity managed (404 se non c'e')
        copiaCampi(req, d);
        // niente persist(): l'entity e' "managed", Hibernate fa l'UPDATE al commit (dirty checking)
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

    // Lookup interno NON annotato con @CacheResult: restituisce l'entity managed, che serve
    // ad aggiorna() per il dirty checking. Chiamarlo da dentro la classe scavalcherebbe
    // comunque l'interceptor CDI (self-invocation), quindi la cache non scatterebbe.
    private Dipendente caricaEntity(Long id) {
        Dipendente d = repository.findById(id);
        if (d == null) {
            throw new NotFoundException("Dipendente " + id + " non trovato");
        }
        return d;
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
}
