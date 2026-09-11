package com.gestionale.dominio.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Le metriche del login, per accorgersi di chi prova a indovinare le password.
 *
 * Le metriche HTTP automatiche contano gia' i 401, ma li mescolano con quelli di
 * qualunque altro endpoint chiamato a sessione scaduta: da li' non si distingue un
 * utente che ha il cookie vecchio da uno script che bussa alla porta del login.
 *
 * Di proposito NON c'e' un tag con lo username. Lo username arriva da fuori: chi ci
 * attacca ne puo' inventare quanti vuole, e ogni valore diverso diventerebbe una
 * serie nuova in Prometheus, fino a riempirlo. Il segnale che serve lo da' gia'
 * {@link #bloccoAttivato}: un blocco scatta solo quando qualcuno ha sbagliato troppe
 * volte, e a quel punto username e IP sono scritti nel log.
 */
@Startup                // creato all'avvio e non al primo login: vedi il costruttore
@ApplicationScoped
public class MetricheLogin {

    /** Tentativi di login, con tag esito=ok|rifiutato|bloccato. */
    private static final String TENTATIVI = "gestionale.login.tentativi";

    /** Blocchi scattati, con tag motivo=username|ip. */
    private static final String BLOCCHI = "gestionale.login.blocchi";

    public static final String ESITO_OK = "ok";
    /** Credenziali sbagliate: il 401. */
    public static final String ESITO_RIFIUTATO = "rifiutato";
    /** Troppi tentativi: il 429, la password non e' stata neanche controllata. */
    public static final String ESITO_BLOCCATO = "bloccato";

    public static final String MOTIVO_USERNAME = "username";
    public static final String MOTIVO_IP = "ip";

    private final MeterRegistry registry;

    @Inject
    public MetricheLogin(MeterRegistry registry) {
        this.registry = registry;
        // Tutti i contatori nascono subito a zero, all'avvio. Se nascessero al primo
        // evento, Prometheus vedrebbe una serie che compare gia' a 1, e increase()
        // non la conterebbe come aumento: il primo blocco in assoluto non farebbe
        // scattare l'alert di Grafana.
        for (String esito : new String[]{ESITO_OK, ESITO_RIFIUTATO, ESITO_BLOCCATO}) {
            registry.counter(TENTATIVI, "esito", esito);
        }
        for (String motivo : new String[]{MOTIVO_USERNAME, MOTIVO_IP}) {
            registry.counter(BLOCCHI, "motivo", motivo);
        }
    }

    public void tentativo(String esito) {
        registry.counter(TENTATIVI, "esito", esito).increment();
    }

    public void bloccoAttivato(String motivo) {
        registry.counter(BLOCCHI, "motivo", motivo).increment();
    }
}
