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
import java.util.List;

@ApplicationScoped
public class TimesheetService {

    @Inject TimesheetRepository repository;
    @Inject DipendenteRepository dipendenteRepository;
    @Inject SitoRepository sitoRepository;

    public List<Timesheet> listaTutti() {
        return repository.listAll();
    }

    public Timesheet trovaPerId(Long id) {
        Timesheet t = repository.findById(id);
        if (t == null) {
            throw new NotFoundException("Timesheet " + id + " non trovato");
        }
        return t;
    }

    @Transactional
    public Timesheet crea(TimesheetRequest req) {
        Timesheet t = new Timesheet();
        applica(t, req);            // logica di mapping condivisa (vedi sotto)
        repository.persist(t);
        return t;
    }

    @Transactional
    public Timesheet aggiorna(Long id, TimesheetRequest req) {
        Timesheet t = trovaPerId(id);
        applica(t, req);
        return t;                   // dirty checking: UPDATE al commit
    }

    @Transactional
    public void elimina(Long id) {
        boolean rimosso = repository.deleteById(id);
        if (!rimosso) {
            throw new NotFoundException("Timesheet " + id + " non trovato");
        }
    }

    // Metodo privato condiviso tra crea e aggiorna: risolve le relazioni e valorizza i campi.
    // Evita di duplicare la stessa logica in due punti (principio DRY).
    private void applica(Timesheet t, TimesheetRequest req) {
        Dipendente dip = dipendenteRepository.findById(req.dipendenteId);
        if (dip == null) {
            throw new NotFoundException("Dipendente " + req.dipendenteId + " non trovato");
        }
        Sito sito = sitoRepository.findById(req.sitoId);
        if (sito == null) {
            throw new NotFoundException("Sito " + req.sitoId + " non trovato");
        }
        t.dipendente = dip;
        t.sito = sito;
        t.dataLavoro = req.dataLavoro;
        t.oreLavorate = req.oreLavorate;
        t.note = req.note;
    }
}