package com.gestionale.dominio.auth.service;

import com.gestionale.dominio.observability.MetricheLogin;
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

/**
 * Test senza Quarkus e senza database: la protezione e' solo logica sui contatori,
 * e l'orologio finto fa passare le ore di blocco in un istante.
 */
class ProtezioneLoginTest {

    private static final String IP = "10.0.0.1";

    private OrologioFinto orologio;
    private SimpleMeterRegistry registry;
    private ProtezioneLogin protezione;

    @BeforeEach
    void prepara() {
        orologio = new OrologioFinto(Instant.now());
        registry = new SimpleMeterRegistry();
        protezione = new ProtezioneLogin(
                new ProtezioneLogin.Regola(5, Duration.ofHours(24)),
                new ProtezioneLogin.Regola(20, Duration.ofHours(1)),
                Duration.ofMinutes(1),
                Duration.ofHours(1),
                new MetricheLogin(registry),
                orologio);
    }

    @Test
    void dopoCinqueFallimentiLoUsernameEBloccato() {
        fallisci("mario", IP, 5);

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> protezione.inizia("mario", IP));
        assertEquals(429, e.getResponse().getStatus());
        assertEquals("60", retryAfter(e));
    }

    @Test
    void quattroFallimentiNonBastano() {
        fallisci("mario", IP, 4);

        assertDoesNotThrow(() -> protezione.inizia("mario", IP).close());
    }

    @Test
    void ilBloccoRaddoppiaAOgniFallimentoInPiu() {
        fallisci("mario", IP, 5);                 // blocco di 1 minuto

        orologio.avanza(Duration.ofMinutes(1));
        fallisci("mario", IP, 1);                 // sesto fallimento: 2 minuti
        assertEquals("120", retryAfter(assertThrows(WebApplicationException.class,
                () -> protezione.inizia("mario", IP))));

        orologio.avanza(Duration.ofMinutes(2));
        fallisci("mario", IP, 1);                 // settimo: 4 minuti
        assertEquals("240", retryAfter(assertThrows(WebApplicationException.class,
                () -> protezione.inizia("mario", IP))));
    }

    @Test
    void ilBloccoNonSuperaIlMassimo() {
        fallisci("mario", IP, 5);
        for (int i = 0; i < 10; i++) {
            orologio.avanza(Duration.ofHours(1));
            fallisci("mario", "10.0.0." + (i + 2), 1);   // IP diversi, per non bloccare l'IP
        }

        assertEquals("3600", retryAfter(assertThrows(WebApplicationException.class,
                () -> protezione.inizia("mario", IP))));
    }

    @Test
    void unLoginRiuscitoAzzeraLoUsername() {
        fallisci("mario", IP, 4);
        protezione.inizia("mario", IP).riuscito();

        fallisci("mario", IP, 4);                 // altri quattro: ancora non bloccato
        assertDoesNotThrow(() -> protezione.inizia("mario", IP).close());
    }

    @Test
    void maiuscoleESpaziNonDannoTentativiInPiu() {
        fallisci("Mario", IP, 2);
        fallisci("MARIO ", IP, 2);
        fallisci(" mario", IP, 1);

        assertThrows(WebApplicationException.class, () -> protezione.inizia("mario", IP));
    }

    @Test
    void iFallimentiVengonoDimenticatiDopoLaMemoria() {
        fallisci("mario", IP, 4);
        orologio.avanza(Duration.ofHours(24));

        fallisci("mario", IP, 4);
        assertDoesNotThrow(() -> protezione.inizia("mario", IP).close());
    }

    @Test
    void troppiUsernameDiversiDalloStessoIpBloccanoLIp() {
        for (int i = 0; i < 20; i++) {
            fallisci("utente" + i, IP, 1);
        }

        assertThrows(WebApplicationException.class, () -> protezione.inizia("nuovo", IP));
        // Da un'altra macchina lo stesso username entra tranquillo
        assertDoesNotThrow(() -> protezione.inizia("nuovo", "10.0.0.99").close());
    }

    @Test
    void iTentativiSuUnoUsernameBloccatoNonConsumanoLIp() {
        fallisci("mario", IP, 5);
        for (int i = 0; i < 50; i++) {
            assertThrows(WebApplicationException.class, () -> protezione.inizia("mario", IP));
        }

        // Il collega dallo stesso ufficio entra ancora
        assertDoesNotThrow(() -> protezione.inizia("luigi", IP).riuscito());
    }

    @Test
    void unLoginRiuscitoNonAzzeraLIp() {
        for (int i = 0; i < 19; i++) {
            fallisci("utente" + i, IP, 1);
        }
        protezione.inizia("mario", IP).riuscito();
        fallisci("utente19", IP, 1);

        assertThrows(WebApplicationException.class, () -> protezione.inizia("altro", IP));
    }

    @Test
    void richiesteInParalleloNonSuperanoLaSoglia() {
        // Cinque tentativi partiti insieme, di cui non si sa ancora l'esito:
        // il sesto non deve passare, anche se nessuno ha ancora fallito.
        for (int i = 0; i < 5; i++) {
            protezione.inizia("mario", IP);
        }

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> protezione.inizia("mario", IP));
        assertEquals("1", retryAfter(e));
    }

    @Test
    void unTentativoInterrottoNonContaComeFallimento() {
        for (int i = 0; i < 10; i++) {
            protezione.inizia("mario", IP).close();    // es. database giu'
        }

        fallisci("mario", IP, 4);
        assertDoesNotThrow(() -> protezione.inizia("mario", IP).close());
    }

    @Test
    void leMetricheContanoEsitiEBlocchi() {
        protezione.inizia("mario", IP).riuscito();
        fallisci("mario", IP, 5);
        assertThrows(WebApplicationException.class, () -> protezione.inizia("mario", IP));

        assertEquals(1, conteggio("gestionale.login.tentativi", "esito", "ok"));
        assertEquals(5, conteggio("gestionale.login.tentativi", "esito", "rifiutato"));
        assertEquals(1, conteggio("gestionale.login.tentativi", "esito", "bloccato"));
        assertEquals(1, conteggio("gestionale.login.blocchi", "motivo", "username"));
    }

    private void fallisci(String username, String ip, int volte) {
        for (int i = 0; i < volte; i++) {
            try (ProtezioneLogin.Tentativo t = protezione.inizia(username, ip)) {
                t.fallito();
            }
        }
    }

    private static String retryAfter(WebApplicationException e) {
        return e.getResponse().getHeaderString(HttpHeaders.RETRY_AFTER);
    }

    private double conteggio(String nome, String tag, String valore) {
        var contatore = registry.find(nome).tag(tag, valore).counter();
        return contatore == null ? 0 : contatore.count();
    }

    /** Un orologio che va avanti solo quando glielo diciamo. */
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
