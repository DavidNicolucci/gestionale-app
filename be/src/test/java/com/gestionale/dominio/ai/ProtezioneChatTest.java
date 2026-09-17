package com.gestionale.dominio.ai;

import com.gestionale.dominio.observability.MetricheAi;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Il tetto alle domande, provato con l'orologio finto: le finestre sono un'ora e un
 * giorno, e aspettarle davvero non e' un'opzione.
 *
 * Quello che va protetto qui non e' tanto "rifiuta la trentunesima" quanto il fatto
 * che il posto si liberi da solo. Un limitatore che conta bene ma non dimentica
 * trasformerebbe un tetto orario in un blocco definitivo, e nessuno se ne
 * accorgerebbe fino al giorno dopo.
 */
class ProtezioneChatTest {

    private static final String MARIO = "mario";

    private OrologioFinto orologio;
    private SimpleMeterRegistry registry;
    private MetricheAi metriche;

    @BeforeEach
    void prepara() {
        orologio = new OrologioFinto(Instant.parse("2026-09-17T09:00:00Z"));
        registry = new SimpleMeterRegistry();
        metriche = new MetricheAi(registry);
    }

    // ---------- limite orario ----------

    @Test
    void finoAllaTrentesimaSiPassa() {
        ProtezioneChat protezione = protezione(30, 200);

        assertDoesNotThrow(() -> domande(protezione, MARIO, 30));
    }

    @Test
    void laTrentunesimaNellaStessaOraVieneRifiutata() {
        ProtezioneChat protezione = protezione(30, 200);
        domande(protezione, MARIO, 30);

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> protezione.nuovaDomanda(MARIO));

        assertEquals(429, e.getResponse().getStatus());
        assertTrue(e.getMessage().contains("limite di domande"), e.getMessage());
    }

    @Test
    void ilRetryAfterDiceQuandoSiLiberaIlPostoDavvero() {
        // Le 30 domande sono spalmate su mezz'ora: la piu' vecchia esce dalla finestra
        // fra mezz'ora esatta, ed e' quello che va scritto nell'header. Una stima fissa
        // ("riprova fra un'ora") manderebbe l'utente a spasso per 30 minuti in piu'.
        ProtezioneChat protezione = protezione(30, 200);
        for (int i = 0; i < 30; i++) {
            protezione.nuovaDomanda(MARIO);
            orologio.avanza(Duration.ofMinutes(1));
        }

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> protezione.nuovaDomanda(MARIO));

        // Sono passati 30 minuti dalla prima: ne restano 30 tondi.
        assertEquals("1800", retryAfter(e));
        assertTrue(e.getMessage().contains("30 minuti"), e.getMessage());
    }

    @Test
    void passataLOraIlPostoSiLiberaDaSolo() {
        ProtezioneChat protezione = protezione(30, 200);
        domande(protezione, MARIO, 30);

        orologio.avanza(Duration.ofMinutes(61));

        assertDoesNotThrow(() -> protezione.nuovaDomanda(MARIO));
    }

    @Test
    void laFinestraScorreEUnaDomandaPerVolta() {
        // Dopo il pieno, avanzando di poco piu' di un'ora dalla PRIMA domanda si
        // libera un posto solo: la finestra scorre, non si azzera.
        ProtezioneChat protezione = protezione(3, 200);
        protezione.nuovaDomanda(MARIO);
        orologio.avanza(Duration.ofMinutes(10));
        protezione.nuovaDomanda(MARIO);
        protezione.nuovaDomanda(MARIO);

        orologio.avanza(Duration.ofMinutes(51));       // fuori finestra solo la prima

        assertDoesNotThrow(() -> protezione.nuovaDomanda(MARIO));
        assertThrows(WebApplicationException.class, () -> protezione.nuovaDomanda(MARIO));
    }

    // ---------- limite giornaliero ----------

    @Test
    void ilTettoDelGiornoReggeAncheSeLOraNonSiRiempieMai() {
        // Una domanda ogni due minuti non tocca mai il limite orario (30 all'ora), ma
        // in un giorno arriva comunque a 200: e' il caso che il solo limite orario non
        // vedrebbe, ed e' quello che costa di piu' in bolletta.
        ProtezioneChat protezione = protezione(30, 200);
        for (int i = 0; i < 200; i++) {
            protezione.nuovaDomanda(MARIO);
            orologio.avanza(Duration.ofMinutes(2));
        }

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> protezione.nuovaDomanda(MARIO));

        assertEquals(429, e.getResponse().getStatus());
        // La prima domanda e' di 6 ore e 40 fa: il posto si libera fra 17 ore e 20,
        // arrotondate per eccesso come nel login (meglio un'attesa in piu' che una di meno).
        assertTrue(e.getMessage().contains("18 ore"), e.getMessage());
    }

    // ---------- il resto ----------

    @Test
    void ogniUtenteHaIlSuoTetto() {
        ProtezioneChat protezione = protezione(3, 200);
        domande(protezione, MARIO, 3);

        assertDoesNotThrow(() -> protezione.nuovaDomanda("luca"));
    }

    @Test
    void loStessoUtenteScrittoDiversoEComunqueLoStessoUtente() {
        // Senza normalizzare, "Mario" e "mario " sarebbero due chiavi e il tetto si
        // aggirerebbe cambiando una maiuscola: stessa regola del login.
        ProtezioneChat protezione = protezione(3, 200);
        domande(protezione, MARIO, 3);

        assertThrows(WebApplicationException.class, () -> protezione.nuovaDomanda(" Mario "));
    }

    @Test
    void aZeroIlLimiteESpento() {
        ProtezioneChat protezione = protezione(0, 0);

        assertDoesNotThrow(() -> domande(protezione, MARIO, 100));
    }

    @Test
    void leDomandeRifiutateSiContanoAParte() {
        ProtezioneChat protezione = protezione(2, 200);
        domande(protezione, MARIO, 2);
        assertThrows(WebApplicationException.class, () -> protezione.nuovaDomanda(MARIO));

        assertEquals(2.0, contatore(MetricheAi.ESITO_OK));
        assertEquals(1.0, contatore(MetricheAi.ESITO_OLTRE_SOGLIA));
    }

    // ---------- aiuti ----------

    private ProtezioneChat protezione(int maxOra, int maxGiorno) {
        return new ProtezioneChat(maxOra, maxGiorno, metriche, orologio);
    }

    private static void domande(ProtezioneChat protezione, String username, int quante) {
        for (int i = 0; i < quante; i++) {
            protezione.nuovaDomanda(username);
        }
    }

    private double contatore(String esito) {
        return registry.counter("gestionale.ai.domande", "esito", esito).count();
    }

    private static String retryAfter(WebApplicationException e) {
        return e.getResponse().getHeaderString(HttpHeaders.RETRY_AFTER);
    }

    /** Un orologio che si sposta quando glielo diciamo noi. */
    private static final class OrologioFinto extends Clock {

        private Instant adesso;

        OrologioFinto(Instant inizio) {
            this.adesso = inizio;
        }

        void avanza(Duration quanto) {
            adesso = adesso.plus(quanto);
        }

        @Override
        public Instant instant() {
            return adesso;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
