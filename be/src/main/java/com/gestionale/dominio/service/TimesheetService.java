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
    // insieme, ogni riga farebbe 2 query in piu'. Le righe annullate restano fuori.
    public List<Timesheet> listaTutti() {
        return repository.listaConRelazioni();
    }

    // Risponde anche sulle righe annullate: serve a ripristinarle e a capire cosa era
    // stato registrato. Il flag e' dentro la risposta.
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

        // Una riga annullata non si corregge: prima la si ripristina. Modificare le ore
        // di una riga che non fa piu' totale vuol dire cambiare un numero che nessuno
        // sta guardando, e ritrovarselo nei conti al ripristino.
        if (t.eliminato) {
            throw new WebApplicationException(
                    "La registrazione " + id + " è stata annullata: ripristinala prima di correggerla", 409);
        }

        applicaPatch(t, req);
        LOG.infof("Timesheet aggiornato: id=%d", id);
        // Niente persist: Hibernate vede le modifiche e fa l'UPDATE a fine transazione.
        return t;
    }

    // Cancellazione logica: la riga resta sul database ma esce da elenchi e totali.
    // E' l'unico dei tre flag che toglie davvero delle ore dai conti, ed e' il suo
    // scopo: annullare una registrazione sbagliata lasciando la traccia che c'era.
    @Transactional
    public void elimina(Long id) {
        Timesheet t = trovaPerId(id);

        if (t.eliminato) {
            LOG.infof("Timesheet %d era gia' annullato: niente da fare", id);
            return;
        }

        t.eliminato = true;
        // Nel log ci mettiamo le ore e il giorno, non solo l'id: quando qualcuno chiede
        // perche' un totale non torna, e' questa riga che glielo spiega.
        LOG.infof("Timesheet annullato: id=%d, dipendenteId=%d, sitoId=%d, data=%s, ore=%s",
                id, t.dipendente.id, t.sito.id, t.dataLavoro, t.oreLavorate);
    }

    // Rimette in conto una registrazione annullata.
    //
    // Non ricontrolliamo ne' il contratto del dipendente ne' lo stato del sito: la riga
    // era gia' stata accettata quando e' stata inserita, e quelle ore sono un fatto
    // avvenuto. E' la stessa ragione per cui applicaPatch non ricontrolla il contratto
    // quando si corregge solo una nota su una riga vecchia.
    @Transactional
    public Timesheet ripristina(Long id) {
        Timesheet t = trovaPerId(id);

        if (!t.eliminato) {
            throw new WebApplicationException(
                    "La registrazione " + id + " non è annullata: non c'è niente da ripristinare", 409);
        }

        t.eliminato = false;
        LOG.infof("Timesheet ripristinato: id=%d", id);
        return t;
    }

    // Carica dipendente e sito e riempie i campi. Usato sia da crea che da aggiorna,
    // per non scrivere due volte lo stesso codice.
    private void applica(Timesheet t, TimesheetRequest req) {
        Dipendente dip = dipendenteRepository.findByIdOptional(req.dipendenteId)
                .orElseThrow(() -> new NotFoundException("Dipendente " + req.dipendenteId + " non trovato"));
        Sito sito = caricaSito(req.sitoId);

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
            t.sito = caricaSito(req.sitoId);
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

    // Il sito su cui si registrano ore nuove deve essere ancora aperto. Le ore gia'
    // registrate su un sito eliminato restano dove sono e continuano a contare: qui
    // stiamo parlando solo di quello che si scrive da adesso in avanti.
    //
    // Il messaggio nomina il sito e dice cosa fare: "sito non trovato" manderebbe
    // l'utente a cercare un errore di battitura che non c'e'.
    private Sito caricaSito(Long sitoId) {
        Sito s = sitoRepository.findByIdOptional(sitoId)
                .orElseThrow(() -> new NotFoundException("Sito " + sitoId + " non trovato"));

        if (s.eliminato) {
            throw new WebApplicationException(
                    "Il sito " + s.nome + " è eliminato: non ci si possono registrare ore nuove."
                            + " Ripristinalo, oppure scegli un altro sito", 409);
        }
        return s;
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
