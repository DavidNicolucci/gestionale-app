package com.gestionale.dominio.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Le metriche dell'import timesheet, tenute tutte in un posto solo.
 *
 * L'import e' asincrono: chi carica il file riceve subito un 202 e non sapra' mai
 * com'e' andata a finire. Le metriche HTTP automatiche misurano quindi solo la
 * consegna del file al broker, che va sempre bene; quello che succede dopo, nel
 * consumer, e' invisibile senza questi contatori.
 *
 * I nomi usano il punto come separatore (convenzione Micrometer); il registry
 * Prometheus li traduce da solo in gestionale_import_file_accodati_total ecc.
 */
@ApplicationScoped
public class MetricheImport {

    /** File messi in coda dall'endpoint di upload. */
    private static final String FILE_ACCODATI = "gestionale.import.file.accodati";

    /** File presi in carico dal consumer, con tag esito=ok|errore. */
    private static final String FILE_ELABORATI = "gestionale.import.file.elaborati";

    /** Righe del foglio Excel, con tag esito=ok|aggiornata|scartata. */
    private static final String RIGHE = "gestionale.import.righe";

    /**
     * Blocchi di righe riprovati dopo un errore passeggero (connessione caduta,
     * deadlock, lock timeout). Se questo contatore sale mentre gli import continuano ad
     * andare a buon fine, la riprova sta facendo esattamente il suo mestiere; se sale
     * insieme a file.elaborati{esito=errore}, il database ha un problema che dura piu'
     * di qualche secondo e la riprova non basta piu'.
     */
    private static final String RIPROVE = "gestionale.import.riprove";

    /**
     * Messaggi arrivati in coda di scarto. E' il contatore su cui vale la pena mettere
     * un alert: ogni unita' e' un file di ore che non e' entrato e che qualcuno deve
     * rilanciare da /api/admin/import.
     */
    private static final String IN_DLQ = "gestionale.import.dlq";

    /** Quanto dura l'elaborazione di un file, con tag esito=ok|errore. */
    private static final String DURATA = "gestionale.import.durata";

    public static final String ESITO_OK = "ok";
    public static final String ESITO_ERRORE = "errore";
    public static final String ESITO_SCARTATA = "scartata";

    /** Riga gia' presente per quel dipendente, quel sito e quel giorno: ore corrette, non duplicate. */
    public static final String ESITO_AGGIORNATA = "aggiornata";

    @Inject
    MeterRegistry registry;

    public void fileAccodato() {
        registry.counter(FILE_ACCODATI).increment();
    }

    public void fileElaborato(String esito) {
        registry.counter(FILE_ELABORATI, "esito", esito).increment();
    }

    public void righe(String esito, int quante) {
        if (quante > 0) {
            registry.counter(RIGHE, "esito", esito).increment(quante);
        }
    }

    /**
     * Fa partire il cronometro. Restituisce un campione che va poi chiuso con
     * {@link #ferma}: il tag "esito" si decide alla fine, quando sappiamo com'e'
     * andata, e per questo il timer non puo' essere creato qui all'inizio.
     */
    public Timer.Sample avviaCronometro() {
        return Timer.start(registry);
    }

    public void ferma(Timer.Sample cronometro, String esito) {
        cronometro.stop(registry.timer(DURATA, "esito", esito));
    }

    public void riprova() {
        registry.counter(RIPROVE).increment();
    }

    public void messaggioInDlq() {
        registry.counter(IN_DLQ).increment();
    }
}
