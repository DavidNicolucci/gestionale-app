package com.gestionale.dominio.imports;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Il registro degli import: tutte le scritture sulla tabella import_job passano di qui.
 *
 * Sono metodi transazionali e quindi devono essere chiamati DA FUORI, attraverso il
 * proxy CDI: e' il motivo per cui l'orchestrazione (rilancio, abbandono) sta in
 * ImportAdminService e non qui dentro. Un metodo che ne chiamasse un altro di questa
 * stessa classe salterebbe il proxy e girerebbe senza transazione.
 */
@ApplicationScoped
public class ImportJobService {

    private static final Logger LOG = Logger.getLogger(ImportJobService.class);

    @Inject ImportJobRepository repository;

    /** Il job aperto o gia' concluso bene su questo contenuto, se esiste. */
    @Transactional
    public Optional<ImportJob> giaPreso(String hash) {
        return repository.perHashGiaPreso(hash);
    }

    /** Nasce qui, al momento dell'upload: da adesso quel file ha una riga che lo segue. */
    @Transactional
    public ImportJob registraUpload(String fileName, String filePath, String hash, String utente) {
        ImportJob job = new ImportJob();
        job.fileName = fileName;
        job.filePath = filePath;
        job.fileHash = hash;
        job.stato = StatoImport.ACCODATO;
        job.caricatoDa = utente;
        job.creatoIl = Instant.now();
        job.aggiornatoIl = Instant.now();
        repository.persist(job);
        LOG.infof("Import %d registrato: file=%s, da=%s", job.id, fileName, utente);
        return job;
    }

    /**
     * Il consumer dichiara di aver preso il messaggio.
     *
     * Torna vuoto quando non c'e' niente da fare: il job e' gia' COMPLETATO (RabbitMQ
     * puo' riconsegnare un messaggio gia' elaborato se l'ack si perde) oppure e' stato
     * ABBANDONATO da un ADMIN mentre era ancora in coda. In tutti e due i casi il
     * messaggio va accettato e buttato via, non rielaborato.
     */
    @Transactional
    public Optional<ImportJob> prendiInCarico(long jobId) {
        ImportJob job = repository.findById(jobId);
        if (job == null) {
            LOG.warnf("Messaggio di import per il job %d, che non esiste: ignorato", jobId);
            return Optional.empty();
        }
        if (job.stato == StatoImport.COMPLETATO || job.stato == StatoImport.ABBANDONATO) {
            LOG.infof("Import %d gia' in stato %s: messaggio ignorato", jobId, job.stato);
            return Optional.empty();
        }

        job.tentativi++;
        job.errore = null;
        job.cambiaStato(StatoImport.IN_CORSO);
        return Optional.of(job);
    }

    /**
     * Somma le righe scartate in fase di LETTURA del foglio (celle illeggibili), che
     * non passano da ImportEsecutore e quindi non verrebbero contate da nessuno.
     */
    @Transactional
    public void aggiungiScartate(long jobId, int quante) {
        ImportJob job = repository.findById(jobId);
        if (job == null) {
            return;
        }
        job.righeScartate += quante;
        job.aggiornatoIl = Instant.now();
    }

    @Transactional
    public void completa(long jobId) {
        ImportJob job = repository.findById(jobId);
        if (job == null) {
            return;
        }
        job.errore = null;
        job.cambiaStato(StatoImport.COMPLETATO);
        LOG.infof("Import %d completato: %d inserite, %d aggiornate, %d scartate (tentativo %d)",
                jobId, job.righeInserite, job.righeAggiornate, job.righeScartate, job.tentativi);
    }

    /**
     * Scrive l'esito negativo sul registro.
     *
     * Puo' fallire a sua volta, ed e' il caso tipico: se l'import e' saltato perche' il
     * database non rispondeva, nemmeno questa scritta arrivera'. Per questo non e' la
     * sola rete: il consumer della DLQ riprova a segnare lo stesso job quando il
     * messaggio rigettato arriva in coda di scarto, cioe' qualche istante dopo, e a quel
     * punto il database spesso e' tornato. Qui ci limitiamo a non far esplodere il
     * chiamante, che deve comunque rilanciare per far dead-letterare il messaggio.
     */
    public void segnaFallito(long jobId, String errore) {
        try {
            scriviFallimento(jobId, errore);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Import %d fallito, ma non si riesce a scriverlo sul registro."
                    + " Ci riprovera' il consumer della DLQ", jobId);
        }
    }

    @Transactional
    public void scriviFallimento(long jobId, String errore) {
        ImportJob job = repository.findById(jobId);
        if (job == null) {
            return;
        }
        // Un job gia' chiuso non si riapre: se e' COMPLETATO l'errore arriva da una
        // riconsegna tardiva, se e' ABBANDONATO la decisione dell'ADMIN vale piu' di
        // un messaggio rimasto in giro.
        if (job.stato == StatoImport.COMPLETATO || job.stato == StatoImport.ABBANDONATO) {
            return;
        }
        // Il motivo preciso lo scrive il consumer, che l'eccezione ce l'ha in mano. Il
        // consumer della DLQ passa di qui poco dopo con un messaggio generico: se
        // sovrascrivesse, l'elenco mostrerebbe "messaggio rigettato" al posto di
        // "connessione al database persa", cioe' il sintomo al posto della causa.
        if (job.stato == StatoImport.FALLITO && job.errore != null && !job.errore.isBlank()) {
            job.aggiornatoIl = Instant.now();
            return;
        }
        job.errore = errore;
        job.cambiaStato(StatoImport.FALLITO);
        LOG.warnf("Import %d segnato come FALLITO: %s", jobId, errore);
    }

    @Transactional
    public ImportJob perId(long id) {
        ImportJob job = repository.findById(id);
        if (job == null) {
            throw new NotFoundException("Import " + id + " non trovato");
        }
        return job;
    }

    @Transactional
    public List<ImportJob> daGuardare(int limite) {
        return repository.daGuardare(limite);
    }

    @Transactional
    public List<ImportJob> perStato(StatoImport stato, int limite) {
        return repository.perStato(stato, limite);
    }

    @Transactional
    public List<ImportJob> ultimi(int limite) {
        return repository.ultimi(limite);
    }

    /** Rimette il job in coda. Torna il job aggiornato, gia' staccato dalla transazione. */
    @Transactional
    public ImportJob preparaRilancio(long id, boolean dallInizio) {
        ImportJob job = perId(id);
        if (dallInizio) {
            // Contatori azzerati insieme al segnaposto: lasciarli sommerebbero il
            // lavoro del tentativo precedente a quello che sta per essere rifatto da
            // capo, e i numeri direbbero il doppio delle righe che ci sono.
            job.ultimaRiga = 0;
            job.righeInserite = 0;
            job.righeAggiornate = 0;
            job.righeScartate = 0;
        }
        job.errore = null;
        job.cambiaStato(StatoImport.ACCODATO);
        return job;
    }

    @Transactional
    public ImportJob segnaAbbandonato(long id, String motivo) {
        ImportJob job = perId(id);
        job.errore = motivo;
        job.cambiaStato(StatoImport.ABBANDONATO);
        return job;
    }
}
