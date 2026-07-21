package com.gestionale.dominio.service;

import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.model.entity.Sito;
import com.gestionale.dominio.model.entity.Timesheet;
import com.gestionale.dominio.repository.DipendenteRepository;
import com.gestionale.dominio.repository.SitoRepository;
import com.gestionale.dominio.repository.TimesheetRepository;
import com.gestionale.dominio.model.dto.TimesheetPatchRequest;
import com.gestionale.dominio.model.dto.TimesheetRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class TimesheetService {

    private static final Logger LOG = Logger.getLogger(TimesheetService.class);

    private final TimesheetRepository repository;
    private final DipendenteRepository dipendenteRepository;
    private final SitoRepository sitoRepository;

    @Inject
    public TimesheetService(TimesheetRepository repository,
                            DipendenteRepository dipendenteRepository,
                            SitoRepository sitoRepository) {
        this.repository = repository;
        this.dipendenteRepository = dipendenteRepository;
        this.sitoRepository = sitoRepository;
    }

    // Non usiamo listAll perche' il DTO mostra dipendente e sito: senza caricarli
    // insieme, ogni riga farebbe 2 query in piu'.
    public List<Timesheet> listaTutti() {
        return repository.listaConRelazioni();
    }

    public Timesheet trovaPerId(Long id) {
        return repository.perIdConRelazioni(id)
                .orElseThrow(() -> new NotFoundException("Timesheet " + id + " non trovato"));
    }

    @Transactional
    public Timesheet crea(TimesheetRequest req) {
        Timesheet t = new Timesheet();
        applica(t, req);
        repository.persist(t);
        LOG.infof("Timesheet creato: id=%d, dipendenteId=%d, data=%s, ore=%s",
                t.id, req.dipendenteId, req.dataLavoro, req.oreLavorate);
        return t;
    }

    // Modifica parziale: cambia solo i campi arrivati nella richiesta.
    @Transactional
    public Timesheet aggiorna(Long id, TimesheetPatchRequest req) {
        Timesheet t = trovaPerId(id);
        applicaPatch(t, req);
        LOG.infof("Timesheet aggiornato: id=%d", id);
        // Niente persist: Hibernate vede le modifiche e fa l'UPDATE a fine transazione.
        return t;
    }

    @Transactional
    public void elimina(Long id) {
        boolean rimosso = repository.deleteById(id);
        if (!rimosso) {
            throw new NotFoundException("Timesheet " + id + " non trovato");
        }
        LOG.infof("Timesheet eliminato: id=%d", id);
    }

    // Carica dipendente e sito e riempie i campi. Usato sia da crea che da aggiorna,
    // per non scrivere due volte lo stesso codice.
    private void applica(Timesheet t, TimesheetRequest req) {
        Dipendente dip = dipendenteRepository.findByIdOptional(req.dipendenteId)
                .orElseThrow(() -> new NotFoundException("Dipendente " + req.dipendenteId + " non trovato"));
        Sito sito = sitoRepository.findByIdOptional(req.sitoId)
                .orElseThrow(() -> new NotFoundException("Sito " + req.sitoId + " non trovato"));

        t.dipendente = dip;
        t.sito = sito;
        t.dataLavoro = req.dataLavoro;
        t.oreLavorate = req.oreLavorate;
        t.note = req.note;
    }

    // Modifica parziale: cambia solo i campi arrivati nella richiesta.
    // Un campo null vuol dire "non toccarlo", quindi lo saltiamo.
    private void applicaPatch(Timesheet t, TimesheetPatchRequest req) {
        if (req.dipendenteId != null) {
            t.dipendente = dipendenteRepository.findByIdOptional(req.dipendenteId)
                    .orElseThrow(() -> new NotFoundException("Dipendente " + req.dipendenteId + " non trovato"));
        }
        if (req.sitoId != null) {
            t.sito = sitoRepository.findByIdOptional(req.sitoId)
                    .orElseThrow(() -> new NotFoundException("Sito " + req.sitoId + " non trovato"));
        }
        if (req.dataLavoro != null) {
            t.dataLavoro = req.dataLavoro;
        }
        if (req.oreLavorate != null) {
            t.oreLavorate = req.oreLavorate;
        }
        if (req.note != null) {
            t.note = req.note;
        }
    }
}
