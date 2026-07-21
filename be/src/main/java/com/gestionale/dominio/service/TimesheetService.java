package com.gestionale.dominio.service;

import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.model.entity.Sito;
import com.gestionale.dominio.model.entity.Timesheet;
import com.gestionale.dominio.repository.DipendenteRepository;
import com.gestionale.dominio.repository.SitoRepository;
import com.gestionale.dominio.repository.TimesheetRepository;
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

    // Non listAll(): il DTO legge dipendente e sito, che sono LAZY. Senza la fetch join
    // ogni riga scatenerebbe 2 query aggiuntive (N+1).
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
        applica(t, req);            // logica di mapping condivisa (vedi sotto)
        repository.persist(t);
        LOG.infof("Timesheet creato: id=%d, dipendenteId=%d, data=%s, ore=%s",
                t.id, req.dipendenteId, req.dataLavoro, req.oreLavorate);
        return t;
    }

    @Transactional
    public Timesheet aggiorna(Long id, TimesheetRequest req) {
        Timesheet t = trovaPerId(id);
        applica(t, req);
        LOG.infof("Timesheet aggiornato: id=%d", id);
        return t;                   // dirty checking: UPDATE al commit
    }

    @Transactional
    public void elimina(Long id) {
        boolean rimosso = repository.deleteById(id);
        if (!rimosso) {
            throw new NotFoundException("Timesheet " + id + " non trovato");
        }
        LOG.infof("Timesheet eliminato: id=%d", id);
    }

    // Metodo privato condiviso tra crea e aggiorna: risolve le relazioni e valorizza i campi.
    // Evita di duplicare la stessa logica in due punti (principio DRY).
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
}
