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
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;

import java.time.LocalDate;

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

        vietaSeFuoriContratto(dip, req.dataLavoro);

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

        // Ricontrolliamo solo se la richiesta ha toccato uno dei due termini del
        // confronto. Controllare sempre vorrebbe dire non poter piu' correggere una nota
        // o un decimale su una riga vecchia, di uno che nel frattempo e' cessato: quelle
        // ore restano un fatto valido, la riga e' gia' stata accettata a suo tempo.
        //
        // Il controllo va in fondo e sulla coppia finale (t.dipendente, t.dataLavoro):
        // che sia cambiato il dipendente, la data o tutti e due, quello che conta e' come
        // resta la riga alla fine.
        if (req.dipendenteId != null || req.dataLavoro != null) {
            vietaSeFuoriContratto(t.dipendente, t.dataLavoro);
        }
    }

    // Si consuntivano ore solo su un contratto che copriva quel giorno.
    //
    // Il confronto e' con la data del lavoro, non con oggi: registrare a novembre le ore
    // di ottobre e' normale, e un contratto finito il 31 ottobre quelle ore le copriva.
    // Al contrario, oggi non si scrive niente su chi e' scaduto ieri.
    //
    // La regola vera sta su Dipendente.sottoContrattoIl(), non qui: la stessa riga la
    // chiama l'import da Excel, che scrive i timesheet senza passare da questo service.
    // Se la scrivessimo qui dentro, l'altra strada la salterebbe.
    private void vietaSeFuoriContratto(Dipendente d, LocalDate dataLavoro) {
        if (d.sottoContrattoIl(dataLavoro)) {
            return;
        }

        // Il messaggio dice quale delle tre condizioni non e' rispettata: "non si puo'"
        // e basta lascerebbe l'utente a indovinare cosa sistemare.
        String motivo;
        if (d.eliminato) {
            motivo = "è stato eliminato";
        } else if (dataLavoro.isBefore(d.dataAssunzione)) {
            motivo = "non era ancora assunto (assunzione il " + d.dataAssunzione + ")";
        } else {
            motivo = "aveva il contratto scaduto (scadenza il " + d.dataScadenza + ")";
        }

        throw new WebApplicationException(
                "Impossibile registrare ore del " + dataLavoro + " per " + d.nome + " "
                        + d.cognome + ": a quella data " + motivo, 409);
    }
}
