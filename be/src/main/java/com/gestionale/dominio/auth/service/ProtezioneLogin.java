package com.gestionale.dominio.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.gestionale.dominio.observability.MetricheLogin;
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
import java.util.Locale;
import java.util.function.IntFunction;

/**
 * Tiene lontano chi prova a indovinare le password bussando al login.
 *
 * BCrypt protegge le password se ci rubano il database, ma non ferma uno script che
 * prova dieci password al secondo sull'endpoint: senza un limite, prima o poi una
 * password debole la trova. Qui contiamo i fallimenti su due chiavi:
 *   - lo username: ferma chi insiste su un account preciso;
 *   - l'IP: ferma chi da una macchina sola prova tanti username diversi, una
 *     password alla volta, restando sotto il limite di ogni singolo account.
 *
 * Superata la soglia la chiave resta bloccata per un po', e ogni fallimento in piu'
 * raddoppia l'attesa (1 min, 2, 4, 8... fino al massimo). E' il "ritardo
 * progressivo" fatto senza addormentare thread: una richiesta bloccata riceve subito
 * un 429 e non costa niente, nemmeno il controllo BCrypt.
 *
 * Contano anche gli username che non esistono: se bloccassimo solo quelli veri, il
 * 429 al posto del 401 direbbe a chi ci attacca quali account esistono.
 *
 * I contatori stanno in memoria: vanno bene finche' il backend e' un'istanza sola.
 * Con piu' istanze dietro un bilanciatore ognuna avrebbe i suoi, e i limiti reali
 * diventerebbero N volte piu' larghi: a quel punto andrebbero spostati su Redis o sul
 * database.
 */
@ApplicationScoped
public class ProtezioneLogin {

    private static final Logger LOG = Logger.getLogger(ProtezioneLogin.class);

    /**
     * Tetto alle chiavi ricordate. Username e IP arrivano da fuori: senza un limite,
     * uno script che inventa username a raffica riempirebbe la memoria.
     */
    private static final long MAX_CHIAVI = 100_000;

    /** Quando rifiutiamo solo perche' un altro tentativo e' ancora in corso. */
    private static final Duration ATTESA_TENTATIVO_IN_CORSO = Duration.ofSeconds(1);

    /** Quanti fallimenti tollerare su una chiave e per quanto ricordarli. */
    public record Regola(int maxFallimenti, Duration memoria) {
    }

    private final Regola regolaUsername;
    private final Regola regolaIp;
    private final Duration bloccoIniziale;
    private final Duration bloccoMassimo;
    private final MetricheLogin metriche;
    private final Clock clock;
    private final Cache<String, Stato> stati;

    @Inject
    public ProtezioneLogin(
            @ConfigProperty(name = "login.protezione.username.max-fallimenti") int maxFallimentiUsername,
            @ConfigProperty(name = "login.protezione.username.memoria") Duration memoriaUsername,
            @ConfigProperty(name = "login.protezione.ip.max-fallimenti") int maxFallimentiIp,
            @ConfigProperty(name = "login.protezione.ip.memoria") Duration memoriaIp,
            @ConfigProperty(name = "login.protezione.blocco-iniziale") Duration bloccoIniziale,
            @ConfigProperty(name = "login.protezione.blocco-massimo") Duration bloccoMassimo,
            MetricheLogin metriche) {
        this(new Regola(maxFallimentiUsername, memoriaUsername),
                new Regola(maxFallimentiIp, memoriaIp),
                bloccoIniziale, bloccoMassimo, metriche, Clock.systemUTC());
    }

    /** Per i test: permette di far scorrere il tempo senza aspettarlo davvero. */
    ProtezioneLogin(Regola regolaUsername, Regola regolaIp, Duration bloccoIniziale,
                    Duration bloccoMassimo, MetricheLogin metriche, Clock clock) {
        this.regolaUsername = regolaUsername;
        this.regolaIp = regolaIp;
        this.bloccoIniziale = bloccoIniziale;
        this.bloccoMassimo = bloccoMassimo;
        this.metriche = metriche;
        this.clock = clock;
        this.stati = Caffeine.newBuilder()
                .maximumSize(MAX_CHIAVI)
                .expireAfter(new ScadenzaStato())
                .build();
    }

    /**
     * Da chiamare prima di controllare la password. Se username o IP sono bloccati
     * lancia subito un 429; altrimenti restituisce il tentativo, su cui va poi
     * chiamato {@link Tentativo#riuscito()} o {@link Tentativo#fallito()}.
     *
     * Il tentativo viene segnato "in corso" gia' adesso, prima di BCrypt, e non
     * quando si scopre com'e' andata. Il controllo della password dura un centinaio
     * di millisecondi: se contassimo solo alla fine, cento richieste partite insieme
     * vedrebbero tutte il contatore a zero e passerebbero tutte.
     */
    public Tentativo inizia(String username, String ip) {
        String chiaveUsername = "username:" + normalizza(username);
        String chiaveIp = "ip:" + (ip == null ? "sconosciuto" : ip);
        Instant adesso = clock.instant();

        // Prima un'occhiata a entrambe senza toccare niente: una richiesta rifiutata
        // per lo username non deve consumare anche i tentativi dell'IP, altrimenti un
        // utente che riprova sul suo account bloccato finirebbe per bloccare tutto
        // l'ufficio che esce dallo stesso IP.
        controllaSenzaContare(chiaveIp, regolaIp, adesso);
        controllaSenzaContare(chiaveUsername, regolaUsername, adesso);

        // Poi la prenotazione vera, atomica chiave per chiave. Se nel frattempo un
        // altro tentativo ha fatto scattare il blocco, la prenotazione dell'IP gia'
        // fatta la restituiamo, per lo stesso motivo di sopra.
        prenota(chiaveIp, regolaIp, adesso);
        try {
            prenota(chiaveUsername, regolaUsername, adesso);
        } catch (WebApplicationException e) {
            rilascia(chiaveIp);
            throw e;
        }
        return new Tentativo(chiaveUsername, chiaveIp, username, ip);
    }

    /** L'esito di un tentativo di login gia' prenotato con {@link #inizia}. */
    public final class Tentativo implements AutoCloseable {

        private final String chiaveUsername;
        private final String chiaveIp;
        private final String username;
        private final String ip;
        private boolean chiuso;

        private Tentativo(String chiaveUsername, String chiaveIp, String username, String ip) {
            this.chiaveUsername = chiaveUsername;
            this.chiaveIp = chiaveIp;
            this.username = username;
            this.ip = ip;
        }

        /**
         * Password giusta: lo username riparte da zero. L'IP invece si tiene i
         * fallimenti accumulati, altrimenti chi ha un account valido potrebbe
         * azzerarsi il contatore facendo login fra un tentativo e l'altro.
         */
        public void riuscito() {
            if (chiudi()) {
                stati.invalidate(chiaveUsername);
                rilascia(chiaveIp);
                metriche.tentativo(MetricheLogin.ESITO_OK);
            }
        }

        public void fallito() {
            if (chiudi()) {
                Instant adesso = clock.instant();
                registraFallimento(chiaveIp, regolaIp, adesso, MetricheLogin.MOTIVO_IP);
                registraFallimento(chiaveUsername, regolaUsername, adesso, MetricheLogin.MOTIVO_USERNAME);
                metriche.tentativo(MetricheLogin.ESITO_RIFIUTATO);
            }
        }

        /**
         * Se il login si interrompe per altro (database giu', errore imprevisto) non
         * sappiamo se la password era giusta: il tentativo si libera senza contarlo.
         */
        @Override
        public void close() {
            if (chiudi()) {
                rilascia(chiaveUsername);
                rilascia(chiaveIp);
            }
        }

        private boolean chiudi() {
            if (chiuso) {
                return false;
            }
            chiuso = true;
            return true;
        }

        private void registraFallimento(String chiave, Regola regola, Instant adesso, String motivo) {
            Stato dopo = stati.asMap().compute(chiave, (k, s) ->
                    attuale(s, adesso).conFallimento(adesso, regola, ProtezioneLogin.this::durataBlocco));
            if (dopo.fineBlocco() != null) {
                Duration durata = Duration.between(adesso, dopo.fineBlocco());
                metriche.bloccoAttivato(motivo);
                LOG.warnf("Login bloccato per %s dopo %d fallimenti (username=%s, ip=%s), per %s",
                        motivo, dopo.fallimenti(), username, ip, durata);
            }
        }
    }

    private void controllaSenzaContare(String chiave, Regola regola, Instant adesso) {
        Stato s = attuale(stati.getIfPresent(chiave), adesso);
        Duration attesa = s.attesa(adesso, regola);
        if (attesa != null) {
            rifiuta(attesa);
        }
    }

    private void prenota(String chiave, Regola regola, Instant adesso) {
        Duration[] attesa = new Duration[1];
        stati.asMap().compute(chiave, (k, s) -> {
            Stato stato = attuale(s, adesso);
            attesa[0] = stato.attesa(adesso, regola);
            return attesa[0] != null ? stato : stato.conTentativoInCorso(adesso, regola);
        });
        if (attesa[0] != null) {
            rifiuta(attesa[0]);
        }
    }

    private void rilascia(String chiave) {
        stati.asMap().computeIfPresent(chiave, (k, s) -> s.senzaTentativoInCorso());
    }

    private void rifiuta(Duration attesa) {
        metriche.tentativo(MetricheLogin.ESITO_BLOCCATO);
        // Arrotondato per eccesso: meglio dire di aspettare un secondo in piu' che
        // far riprovare l'utente quando e' ancora bloccato.
        long secondi = Math.max(1, (attesa.toMillis() + 999) / 1000);
        long minuti = (secondi + 59) / 60;
        String messaggio = minuti <= 1
                ? "Troppi tentativi di accesso. Riprova fra un minuto."
                : "Troppi tentativi di accesso. Riprova fra " + minuti + " minuti.";
        throw new WebApplicationException(messaggio, Response.status(Response.Status.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, secondi)
                .build());
    }

    /** Uno stato scaduto vale come nessuno stato: i vecchi fallimenti sono dimenticati. */
    private Stato attuale(Stato s, Instant adesso) {
        return s == null || !adesso.isBefore(s.scadenza()) ? Stato.NUOVO : s;
    }

    /**
     * 1 minuto al primo blocco, poi il doppio a ogni fallimento in piu', fino al
     * massimo. Lo spostamento e' limitato per non far traboccare il long.
     */
    private Duration durataBlocco(int fallimentiOltreSoglia) {
        Duration durata = bloccoIniziale.multipliedBy(1L << Math.min(fallimentiOltreSoglia, 30));
        return durata.compareTo(bloccoMassimo) > 0 ? bloccoMassimo : durata;
    }

    /**
     * Username in minuscolo e senza spazi ai lati: SQL Server confronta le stringhe
     * ignorando maiuscole e spazi finali, quindi "Mario", "mario" e "mario " sono lo
     * stesso account. Se fossero chiavi diverse, basterebbe cambiare una lettera per
     * avere altri cinque tentativi.
     */
    private static String normalizza(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Quello che sappiamo di una chiave. Immutabile: ogni modifica passa da
     * compute(), che la rende atomica anche con richieste in parallelo.
     *
     * @param fallimenti     password sbagliate da quando la chiave e' "pulita"
     * @param inCorso        tentativi partiti di cui non sappiamo ancora l'esito
     * @param fineBlocco     fino a quando la chiave e' bloccata (null = non lo e')
     * @param scadenza       quando dimenticare tutto
     */
    private record Stato(int fallimenti, int inCorso, Instant fineBlocco, Instant scadenza) {

        /** Nessun fallimento, niente da ricordare: e' gia' "scaduto" per costruzione. */
        static final Stato NUOVO = new Stato(0, 0, null, Instant.MIN);

        /**
         * Quanto deve aspettare chi bussa adesso, o null se puo' provare.
         *
         * Oltre al blocco vero c'e' il caso dei tentativi in corso: se quelli partiti
         * bastano gia' a raggiungere la soglia, il prossimo aspetta di sapere come
         * sono andati. Superata la soglia (a blocco scaduto) si prova uno alla volta.
         */
        Duration attesa(Instant adesso, Regola regola) {
            if (fineBlocco != null && adesso.isBefore(fineBlocco)) {
                return Duration.between(adesso, fineBlocco);
            }
            if (inCorso > 0 && fallimenti + inCorso >= regola.maxFallimenti()) {
                return ATTESA_TENTATIVO_IN_CORSO;
            }
            return null;
        }

        Stato conTentativoInCorso(Instant adesso, Regola regola) {
            return new Stato(fallimenti, inCorso + 1, fineBlocco,
                    piuTardi(scadenza, adesso.plus(regola.memoria())));
        }

        Stato senzaTentativoInCorso() {
            return new Stato(fallimenti, Math.max(0, inCorso - 1), fineBlocco, scadenza);
        }

        Stato conFallimento(Instant adesso, Regola regola, IntFunction<Duration> durataBlocco) {
            // Dal Tentativo si arriva qui una volta sola, dopo la prenotazione: il
            // tentativo smette di essere "in corso" e diventa un fallimento.
            int nuoviFallimenti = fallimenti + 1;
            Instant nuovaFine = nuoviFallimenti >= regola.maxFallimenti()
                    ? adesso.plus(durataBlocco.apply(nuoviFallimenti - regola.maxFallimenti()))
                    : null;
            Instant nuovaScadenza = piuTardi(adesso.plus(regola.memoria()), nuovaFine);
            return new Stato(nuoviFallimenti, Math.max(0, inCorso - 1), nuovaFine, nuovaScadenza);
        }

        private static Instant piuTardi(Instant a, Instant b) {
            if (b == null) return a;
            return a.isAfter(b) ? a : b;
        }
    }

    /**
     * Dice a Caffeine quando togliere una chiave dalla memoria: alla sua scadenza,
     * che puo' allungarsi a ogni fallimento. Una durata fissa non andrebbe bene:
     * un blocco lungo verrebbe dimenticato prima di finire.
     */
    private final class ScadenzaStato implements Expiry<String, Stato> {

        @Override
        public long expireAfterCreate(String chiave, Stato stato, long adessoNanos) {
            return nanosFinoA(stato.scadenza());
        }

        @Override
        public long expireAfterUpdate(String chiave, Stato stato, long adessoNanos, long restantiNanos) {
            return nanosFinoA(stato.scadenza());
        }

        @Override
        public long expireAfterRead(String chiave, Stato stato, long adessoNanos, long restantiNanos) {
            return restantiNanos;           // leggere una chiave non la tiene in vita
        }

        private long nanosFinoA(Instant scadenza) {
            Instant adesso = clock.instant();
            return scadenza.isAfter(adesso) ? Duration.between(adesso, scadenza).toNanos() : 0;
        }
    }
}
