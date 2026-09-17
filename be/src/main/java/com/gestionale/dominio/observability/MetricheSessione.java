package com.gestionale.dominio.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Le metriche della sessione: quanto dura davvero e quando viene chiusa.
 *
 * Login e import hanno i loro contatori, la sessione no: fra i due estremi - il
 * login che riesce e l'import che gira - c'e' un pezzo di vita dell'utente che oggi
 * non si vede. La sessione e' "scorrevole" (vedi
 * {@link com.gestionale.dominio.auth.filter.FiltroSessione}), cioe' si rinnova da
 * sola mentre si lavora, ma che funzioni come previsto e' finora un'ipotesi: se il
 * rinnovo non scattasse, o se qualcosa revocasse i token piu' spesso del dovuto, gli
 * utenti tornerebbero alla pagina di accesso e nessuno se ne accorgerebbe da qui.
 *
 * I 401 delle metriche HTTP automatiche non bastano a dirlo: mescolano il token
 * revocato, il cookie scaduto e la chiamata senza credenziali, che sono tre storie
 * diverse. {@link #chiusa} li separa per motivo.
 *
 * Come in {@link MetricheLogin} non c'e' nessun tag con lo username: i tag qui sono
 * tutti valori decisi dal codice, quindi le serie in Prometheus restano poche e
 * contate. Chi e' l'utente sta gia' nel log della revoca.
 */
@Startup                // i contatori nascono a zero all'avvio: vedi MetricheLogin
@ApplicationScoped
public class MetricheSessione {

    /** Revoche fatte dal codice, con tag motivo=logout|cambio-password|altro. */
    private static final String REVOCHE = "gestionale.sessione.revoche";

    /** Richieste respinte con 401 dal filtro, con tag motivo. */
    private static final String CHIUSE = "gestionale.sessione.chiuse";

    /** Passaggi dal filtro in entrata, con tag esito=rinnovata|tetto-raggiunto|troppo-presto. */
    private static final String RINNOVI = "gestionale.sessione.rinnovi";

    public static final String MOTIVO_LOGOUT = "logout";
    public static final String MOTIVO_CAMBIO_PASSWORD = "cambio-password";
    /** Qualunque altra revoca: tenuto apposta generico, cosi' un motivo nuovo non crea una serie nuova. */
    public static final String MOTIVO_ALTRO = "altro";

    /** Il token porta un'epoca diversa da quella sul database: logout o cambio password. */
    public static final String CHIUSA_REVOCATA = "revocata";
    /** L'account e' stato disattivato mentre la sessione era aperta. */
    public static final String CHIUSA_DISATTIVATO = "disattivato";
    /** L'utente non esiste piu': token che parla di un account cancellato. */
    public static final String CHIUSA_SCONOSCIUTO = "sconosciuto";
    /** Token senza claim token_epoch, emesso prima che il controllo esistesse. */
    public static final String CHIUSA_SENZA_EPOCA = "senza-epoca";

    /** Token rifirmato con la scadenza spostata in avanti: la sessione scorrevole sta funzionando. */
    public static final String RINNOVO_FATTO = "rinnovata";
    /** Tetto di sessione.durata-massima raggiunto: qui l'utente torna alla pagina di accesso. */
    public static final String RINNOVO_TETTO = "tetto-raggiunto";
    /** Non e' ancora passato sessione.rinnovo-minimo: il caso piu' comune, e va bene cosi'. */
    public static final String RINNOVO_TROPPO_PRESTO = "troppo-presto";

    private final MeterRegistry registry;

    @Inject
    public MetricheSessione(MeterRegistry registry) {
        this.registry = registry;
        for (String motivo : new String[]{MOTIVO_LOGOUT, MOTIVO_CAMBIO_PASSWORD, MOTIVO_ALTRO}) {
            registry.counter(REVOCHE, "motivo", motivo);
        }
        for (String motivo : new String[]{CHIUSA_REVOCATA, CHIUSA_DISATTIVATO,
                CHIUSA_SCONOSCIUTO, CHIUSA_SENZA_EPOCA}) {
            registry.counter(CHIUSE, "motivo", motivo);
        }
        for (String esito : new String[]{RINNOVO_FATTO, RINNOVO_TETTO, RINNOVO_TROPPO_PRESTO}) {
            registry.counter(RINNOVI, "esito", esito);
        }
    }

    public void revocata(String motivo) {
        registry.counter(REVOCHE, "motivo", normalizza(motivo)).increment();
    }

    public void chiusa(String motivo) {
        registry.counter(CHIUSE, "motivo", motivo).increment();
    }

    public void rinnovo(String esito) {
        registry.counter(RINNOVI, "esito", esito).increment();
    }

    /**
     * Il motivo della revoca e' una stringa libera, scritta per il log. Qui dentro
     * diventa un tag, e un tag con valori liberi e' il modo classico di riempire
     * Prometheus di serie: tutto quello che non riconosciamo finisce in "altro".
     */
    private static String normalizza(String motivo) {
        if (MOTIVO_LOGOUT.equals(motivo)) {
            return MOTIVO_LOGOUT;
        }
        return "cambio password".equals(motivo) || MOTIVO_CAMBIO_PASSWORD.equals(motivo)
                ? MOTIVO_CAMBIO_PASSWORD
                : MOTIVO_ALTRO;
    }
}
