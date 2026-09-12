package com.gestionale.dominio.imports;

import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.model.entity.Sito;
import com.gestionale.dominio.model.entity.Timesheet;
import com.gestionale.dominio.model.enums.FiltroStato;
import com.gestionale.dominio.repository.DipendenteRepository;
import com.gestionale.dominio.repository.SitoRepository;
import com.gestionale.dominio.repository.TimesheetRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scrive un blocco di righe, in una transazione sua.
 *
 * Sta in una classe separata dal consumer per una ragione tecnica precisa: @Transactional
 * funziona solo quando il metodo viene chiamato DA FUORI, attraverso il proxy CDI. Un
 * metodo annotato che il consumer chiamasse su se' stesso girerebbe senza transazione
 * e le righe non verrebbero mai scritte.
 *
 * PERCHE' A BLOCCHI. Prima l'import era un solo @Transactional sull'intero metodo del
 * consumer. Su un file da 5.000 righe questo voleva dire due cose, tutte e due brutte:
 * una transazione aperta per minuti che teneva impegnata una connessione del pool per
 * tutto il tempo, e un errore alla riga 4.999 che annullava anche le 4.998 righe buone.
 * Con i blocchi, ogni pezzo committato resta committato: un errore in fondo al file
 * costa il blocco in corso, non il file intero.
 */
@ApplicationScoped
public class ImportEsecutore {

    private static final Logger LOG = Logger.getLogger(ImportEsecutore.class);

    @Inject DipendenteRepository dipendenteRepo;
    @Inject SitoRepository sitoRepo;
    @Inject TimesheetRepository timesheetRepo;
    @Inject ImportJobRepository jobRepo;

    /**
     * Salva le righe del blocco e aggiorna il job, tutto nella stessa transazione.
     *
     * Che i contatori e il segnaposto (ultimaRiga) stiano DENTRO la stessa transazione
     * delle righe non e' un dettaglio: e' cio' che rende il segnaposto attendibile.
     * Se si aggiornasse a parte, un crash fra i due commit lascerebbe scritto "arrivato
     * alla riga 2.000" con le righe non salvate, e il rilancio ripartirebbe da un punto
     * che non esiste, saltando duemila righe.
     */
    @Transactional
    public EsitoBlocco salvaBlocco(long jobId, List<RigaImport> righe) {
        // Le entita' si risolvono dentro la transazione corrente e non si tengono da un
        // blocco all'altro: un'entita' di una transazione chiusa e' staccata, e usarla
        // per una FK darebbe un errore che non c'entra niente col file. La cache vale
        // quindi per il blocco, che e' comunque il grosso del risparmio: le stesse
        // persone e gli stessi cantieri tornano decine di volte nello stesso foglio.
        Map<String, Optional<Dipendente>> dipendenti = new HashMap<>();
        Map<String, Optional<Sito>> siti = new HashMap<>();

        int inserite = 0;
        int aggiornate = 0;
        int scartate = 0;

        for (RigaImport riga : righe) {
            // Cerchiamo fra TUTTI, eliminati compresi: se li escludessimo qui, una riga
            // intestata a un eliminato direbbe "non trovato" e chi corregge il file
            // andrebbe a caccia di un codice fiscale sbagliato che sbagliato non e'.
            Optional<Dipendente> dip = dipendenti.computeIfAbsent(riga.codiceFiscale(),
                    cf -> dipendenteRepo.perCodiceFiscale(cf, FiltroStato.TUTTI));
            Optional<Sito> sito = siti.computeIfAbsent(riga.nomeSito(),
                    nome -> sitoRepo.perNome(nome, FiltroStato.TUTTI));

            if (dip.isEmpty() || sito.isEmpty()) {
                LOG.warnf("Riga %d ignorata: dipendente o sito non trovato (cf=%s, sito=%s)",
                        riga.numeroRiga(), riga.codiceFiscale(), riga.nomeSito());
                scartate++;
                continue;
            }

            // Su un sito eliminato non si scrivono ore nuove, come sul dipendente
            // eliminato. Riga saltata e non eccezione: un file da 500 righe non deve
            // finire in coda di scarto per una riga intestata a un cantiere chiuso.
            if (sito.get().eliminato) {
                LOG.warnf("Riga %d ignorata: il sito %s e' eliminato (cf=%s)",
                        riga.numeroRiga(), riga.nomeSito(), riga.codiceFiscale());
                scartate++;
                continue;
            }

            // Le ore si consuntivano solo su un contratto che copriva quel giorno. Il
            // confronto e' con la data della riga, non con oggi: caricare a novembre il
            // foglio di ottobre e' normale, e un contratto finito il 31 ottobre quelle
            // ore le copriva.
            if (!dip.get().sottoContrattoIl(riga.data())) {
                LOG.warnf("Riga %d ignorata: %s non era sotto contratto il %s (cf=%s)",
                        riga.numeroRiga(), nominativo(dip.get()), riga.data(), riga.codiceFiscale());
                scartate++;
                continue;
            }

            if (scriviOre(dip.get(), sito.get(), riga)) {
                inserite++;
            } else {
                aggiornate++;
            }
        }

        aggiornaJob(jobId, righe, inserite, aggiornate, scartate);
        return new EsitoBlocco(inserite, aggiornate, scartate);
    }

    /**
     * Inserisce le ore, oppure corregge quelle gia' presenti per lo stesso dipendente,
     * lo stesso sito e lo stesso giorno. Torna true se ha inserito.
     *
     * Questo e' il motivo per cui ricaricare lo stesso file non raddoppia piu' le ore.
     * Il controllo sta qui E sul database (indice uq_timesheet_giorno): qui perche' il
     * secondo caricamento deve funzionare e correggere, non esplodere; sul database
     * perche' un controllo solo applicativo lo rispetta chi passa di qui, non chi lancia
     * una INSERT a mano.
     *
     * Sovrascrive invece di sommare: un file di ore e' la fotografia di un periodo, non
     * un movimento contabile. Chi ricarica il file corretto si aspetta di vedere il
     * valore corretto, non il vecchio piu' il nuovo.
     */
    private boolean scriviOre(Dipendente dip, Sito sito, RigaImport riga) {
        Optional<Timesheet> esistente =
                timesheetRepo.perGiornoLavorato(dip.id, sito.id, riga.data());

        if (esistente.isPresent()) {
            Timesheet t = esistente.get();
            // compareTo e non equals: 7.5 e 7.50 sono lo stesso numero ma due BigDecimal
            // diversi, ed equals direbbe di no facendo una UPDATE inutile per ogni riga
            // a ogni rilancio.
            if (t.oreLavorate.compareTo(riga.ore()) != 0) {
                LOG.infof("Riga %d: ore corrette da %s a %s (cf=%s, sito=%s, giorno=%s)",
                        riga.numeroRiga(), t.oreLavorate, riga.ore(),
                        riga.codiceFiscale(), riga.nomeSito(), riga.data());
                t.oreLavorate = riga.ore();
            }
            return false;
        }

        Timesheet ts = new Timesheet();
        ts.dipendente = dip;
        ts.sito = sito;
        ts.dataLavoro = riga.data();
        ts.oreLavorate = riga.ore();
        timesheetRepo.persist(ts);
        return true;
    }

    private void aggiornaJob(long jobId, List<RigaImport> righe, int inserite, int aggiornate, int scartate) {
        ImportJob job = jobRepo.findById(jobId);
        if (job == null) {
            // Non e' un motivo per annullare righe gia' valide: le ore restano, il
            // registro perde un aggiornamento. Puo' succedere solo se qualcuno ha
            // cancellato la riga del job mentre l'import girava.
            LOG.warnf("Job %d non trovato: blocco salvato ma contatori non aggiornati", jobId);
            return;
        }
        job.righeInserite += inserite;
        job.righeAggiornate += aggiornate;
        job.righeScartate += scartate;
        if (!righe.isEmpty()) {
            job.ultimaRiga = righe.get(righe.size() - 1).numeroRiga();
        }
        job.aggiornatoIl = Instant.now();
    }

    // Nel log il nome e non solo il codice fiscale: chi legge l'esito dell'import deve
    // capire di chi si parla senza andarlo a cercare in anagrafica.
    private String nominativo(Dipendente d) {
        return d.nome + " " + d.cognome;
    }
}
