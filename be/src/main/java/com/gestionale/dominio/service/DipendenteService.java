package com.gestionale.dominio.service;

import com.gestionale.dominio.model.dto.DipendenteRequest;
import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.repository.DipendenteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;

@ApplicationScoped
public class DipendenteService {

    @Inject
    DipendenteRepository repository;     // inject del repository (CDI)

    public List<Dipendente> listaTutti() {
        return repository.listAll();     // metodo fornito da Panache
    }

    public Dipendente trovaPerId(Long id) {
        Dipendente d = repository.findById(id);
        if (d == null) {
            throw new NotFoundException("Dipendente " + id + " non trovato");
        }
        return d;
    }

    @Transactional                       // apre una transazione: o tutto va a buon fine, o rollback
    public Dipendente crea(DipendenteRequest req) {
        // Regola di business: CF univoco. Controllo applicativo + vincolo DB come rete di sicurezza.
        if (repository.count("codiceFiscale", req.codiceFiscale) > 0) {
            throw new WebApplicationException(
                    "Esiste già un dipendente con codice fiscale " + req.codiceFiscale, 409);
        }

        Dipendente d = new Dipendente();
        d.nome = req.nome;
        d.cognome = req.cognome;
        d.codiceFiscale = req.codiceFiscale;
        d.dataNascita = req.dataNascita;
        d.nazionalita = req.nazionalita;
        d.tipoContratto = req.tipoContratto;
        d.dataAssunzione = req.dataAssunzione;
        d.dataScadenza = req.dataScadenza;

        repository.persist(d);           // INSERT
        return d;
    }

    @Transactional
    public Dipendente aggiorna(Long id, DipendenteRequest req) {
        Dipendente d = trovaPerId(id);   // riusa la logica di lookup (404 se non c'è)
        d.nome = req.nome;
        d.cognome = req.cognome;
        d.codiceFiscale = req.codiceFiscale;
        d.dataNascita = req.dataNascita;
        d.nazionalita = req.nazionalita;
        d.tipoContratto = req.tipoContratto;
        d.dataAssunzione = req.dataAssunzione;
        d.dataScadenza = req.dataScadenza;
        // niente persist(): l'entity è "managed", Hibernate fa l'UPDATE al commit (dirty checking)
        return d;
    }

    @Transactional
    public void elimina(Long id) {
        boolean rimosso = repository.deleteById(id);
        if (!rimosso) {
            throw new NotFoundException("Dipendente " + id + " non trovato");
        }
    }
}