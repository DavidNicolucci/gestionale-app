package com.gestionale.dominio.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gestionale.dominio.observability.MetricheAi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Un tetto alle domande che ogni utente puo' fare all'assistente.
 *
 * Il problema non e' la sicurezza ma la spesa: ogni domanda e' una chiamata a Gemini
 * che si paga a token, e prima di questa classe l'unico limite era @Size(max=2000)
 * sul testo della singola domanda - che dice quanto puo' essere lunga una domanda,
 * non quante se ne possono fare. Mille di fila erano mille chiamate.
 *
 * L'idea e' la stessa di {@link com.gestionale.dominio.auth.service.ProtezioneLogin}:
 * un contatore per utente e una soglia. Cambia il conto, perche' cambia cosa vogliamo
 * evitare. Li' si contano i FALLIMENTI e chi sbaglia troppo viene bloccato a tempo
 * crescente; qui si contano le domande RIUSCITE, perche' e' la domanda andata a buon
 * fine quella che costa, e il limite non e' una punizione: appena la domanda piu'
 * vecchia esce dalla finestra, il posto si libera da solo.
 *
 * Le finestre sono scorrevoli e non a orologio (non "dalle 9 alle 10"): con le
 * finestre fisse chi arriva a fine ora ne fa 30 e poi altre 30 un minuto dopo, cioe'
 * il doppio del limite nel momento peggiore. Scorrevole vuol dire anche che il
 * Retry-After e' esatto e non stimato: sappiamo quando scade la domanda piu' vecchia,
 * quindi sappiamo al secondo quando ci sara' di nuovo posto.
 *
 * Il limite vale per lo username, che arriva dal token e non dal corpo della
 * richiesta: e' l'unica identita' che il client non puo' scegliersi. Vale per tutti,
 * ADMIN compresi: la spesa e' la stessa, e un'esenzione renderebbe il tetto una cosa
 * che si aggira cambiando ruolo.
 *
 * I contatori stanno in memoria, come quelli del login: con piu' istanze dietro un
 * bilanciatore ognuna avrebbe i suoi e il limite vero diventerebbe N volte piu' largo.
 * Per una spesa da tenere sotto controllo e' comunque meglio di niente, ma il giorno
 * che le istanze diventano due questo va su Redis.
 */
@ApplicationScoped
public class ProtezioneChat {

    private static final Logger LOG = Logger.getLogger(ProtezioneChat.class);

    /**
     * Tetto agli utenti ricordati. Gli username qui vengono dal token, quindi sono
     * quelli veri e sono pochi; il limite c'e' per la stessa ragione per cui c'e' in
     * {@link com.gestionale.dominio.auth.service.RevocaSessioni}: una mappa senza
     * tetto e' una mappa che un giorno cresce.
     */
    private static final long MAX_UTENTI = 10_000;

    static final Duration FINESTRA_ORA = Duration.ofHours(1);
    static final Duration FINESTRA_GIORNO = Duration.ofDays(1);

    private final int maxOra;
    private final int maxGiorno;
    private final MetricheAi metriche;
    private final Clock clock;

    /** Per utente, gli istanti delle domande accettate nelle ultime 24 ore, dalla piu' vecchia. */
    private final Cache<String, List<Instant>> domande;

    @Inject
    public ProtezioneChat(@ConfigProperty(name = "chat.limite.ora") int maxOra,
                          @ConfigProperty(name = "chat.limite.giorno") int maxGiorno,
                          MetricheAi metriche) {
        this(maxOra, maxGiorno, metriche, Clock.systemUTC());
    }

    /** Per i test: permette di far scorrere le ore senza aspettarle davvero. */
    ProtezioneChat(int maxOra, int maxGiorno, MetricheAi metriche, Clock clock) {
        this.maxOra = maxOra;
        this.maxGiorno = maxGiorno;
        this.metriche = metriche;
        this.clock = clock;
        this.domande = Caffeine.newBuilder()
                .maximumSize(MAX_UTENTI)
                // Dopo un giorno senza domande non c'e' piu' niente da ricordare: tutti
                // gli istanti nella lista sarebbero comunque fuori finestra.
                .expireAfterWrite(FINESTRA_GIORNO)
                .build();
    }

    /**
     * Da chiamare prima di girare la domanda a Gemini. Se l'utente ha finito le sue,
     * lancia un 429 con Retry-After; altrimenti segna la domanda e torna.
     *
     * Segna subito, prima della risposta: la chiamata a Gemini dura secondi, e se
     * contassimo alla fine venti domande partite insieme vedrebbero tutte il contatore
     * fermo e passerebbero tutte. E' lo stesso motivo per cui il login prenota il
     * tentativo prima di BCrypt.
     */
    public void nuovaDomanda(String username) {
        Instant adesso = clock.instant();
        Duration[] attesa = new Duration[1];
        int[] quante = new int[2];              // [nell'ora, nel giorno], per il log

        domande.asMap().compute(chiave(username), (k, precedenti) -> {
            List<Instant> nelGiorno = dentroLaFinestra(precedenti, adesso, FINESTRA_GIORNO);
            List<Instant> nellOra = dentroLaFinestra(nelGiorno, adesso, FINESTRA_ORA);
            quante[0] = nellOra.size();
            quante[1] = nelGiorno.size();

            // Prima il limite orario: e' il piu' stretto, ed e' quello che si tocca per
            // primo. Il Retry-After lo da' la domanda piu' vecchia della finestra, che
            // e' esattamente quella che liberera' il posto.
            if (pieno(nellOra.size(), maxOra)) {
                attesa[0] = Duration.between(adesso, nellOra.get(0).plus(FINESTRA_ORA));
                return nelGiorno;
            }
            if (pieno(nelGiorno.size(), maxGiorno)) {
                attesa[0] = Duration.between(adesso, nelGiorno.get(0).plus(FINESTRA_GIORNO));
                return nelGiorno;
            }

            List<Instant> aggiornate = new ArrayList<>(nelGiorno);
            aggiornate.add(adesso);
            return List.copyOf(aggiornate);
        });

        if (attesa[0] != null) {
            metriche.domanda(MetricheAi.ESITO_OLTRE_SOGLIA);
            LOG.infof("Limite domande assistente raggiunto da %s (%d nell'ultima ora, %d nell'ultimo giorno)",
                    username, quante[0], quante[1]);
            throw rifiuta(attesa[0]);
        }
        metriche.domanda(MetricheAi.ESITO_OK);
    }

    /** A zero (o sotto) il limite e' spento: comodo in sviluppo, e lo dice il file properties. */
    private static boolean pieno(int quante, int massimo) {
        return massimo > 0 && quante >= massimo;
    }

    /** Le domande ancora dentro la finestra, nello stesso ordine: la piu' vecchia per prima. */
    private static List<Instant> dentroLaFinestra(List<Instant> istanti, Instant adesso, Duration finestra) {
        if (istanti == null || istanti.isEmpty()) {
            return List.of();
        }
        Instant taglio = adesso.minus(finestra);
        return istanti.stream().filter(i -> i.isAfter(taglio)).toList();
    }

    private WebApplicationException rifiuta(Duration attesa) {
        // Arrotondato per eccesso, come nel login: meglio un secondo in piu' che
        // rimandare l'utente quando il posto non si e' ancora liberato.
        long secondi = Math.max(1, (attesa.toMillis() + 999) / 1000);
        String messaggio = "Hai raggiunto il limite di domande all'assistente. Riprova fra "
                + quando(secondi) + ".";
        return new WebApplicationException(messaggio, Response.status(Response.Status.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, secondi)
                .build());
    }

    /** L'attesa detta come la direbbe una persona: il messaggio lo legge un utente, non un programma. */
    private static String quando(long secondi) {
        long minuti = (secondi + 59) / 60;
        if (minuti <= 1) {
            return "un minuto";
        }
        if (minuti < 60) {
            return minuti + " minuti";
        }
        long ore = (minuti + 59) / 60;
        return ore == 1 ? "un'ora" : ore + " ore";
    }

    /** Stessa normalizzazione del login: "Mario" e "mario " sono lo stesso account. */
    private static String chiave(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
