package com.gestionale.dominio.auth.service;

import io.smallrye.jwt.build.JwtSignatureException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test senza Quarkus e senza database: qui interessa solo quando scade la sessione,
 * e l'orologio finto fa passare le ore in un istante.
 *
 * Il calcolo della scadenza e' la parte che tiene in piedi sia la comodita' (la
 * sessione scorre finche' si lavora) sia il limite (non scorre all'infinito), e
 * sbagliarlo non si vedrebbe subito: si vedrebbe fra dodici ore.
 */
class EmissioneTokenTest {

    private static final String ISSUER = "https://gestionale.local/issuer";
    private static final Duration INATTIVITA = Duration.ofMinutes(30);
    private static final Duration TETTO = Duration.ofHours(12);

    private final Instant login = Instant.parse("2026-09-12T09:00:00Z");

    private EmissioneToken emissione(Instant adesso, Duration tetto) {
        return new EmissioneToken(ISSUER, INATTIVITA, tetto, Clock.fixed(adesso, ZoneOffset.UTC));
    }

    @Test
    void appenaFattoIlLoginLaSessioneDuraQuantoLInattivita() {
        EmissioneToken e = emissione(login, TETTO);
        assertEquals(login.plus(INATTIVITA), e.scadenza(login));
    }

    @Test
    void aOgniRinnovoLaScadenzaSiSpostaInAvanti() {
        // Dieci minuti dopo il login l'utente fa un'altra richiesta: la sessione non
        // scade piu' alle 9:30 ma alle 9:40. E' tutto il punto della sessione
        // scorrevole: chi sta lavorando non viene buttato fuori.
        Instant dieciMinutiDopo = login.plus(Duration.ofMinutes(10));
        EmissioneToken e = emissione(dieciMinutiDopo, TETTO);
        assertEquals(dieciMinutiDopo.plus(INATTIVITA), e.scadenza(login));
    }

    @Test
    void aRidossoDelTettoLaScadenzaSiFermaAlTetto() {
        // Undici ore e mezza dopo il login: l'inattivita' porterebbe a 12h e 15m, ma
        // il tetto e' a 12h. Vince il tetto, altrimenti non sarebbe un tetto.
        Instant quasiAlTetto = login.plus(Duration.ofHours(11)).plus(Duration.ofMinutes(45));
        EmissioneToken e = emissione(quasiAlTetto, TETTO);
        assertEquals(login.plus(TETTO), e.scadenza(login));
    }

    @Test
    void oltreIlTettoNonVieneEmessoNessunToken() {
        EmissioneToken e = emissione(login.plus(TETTO), TETTO);
        assertTrue(e.emetti("admin", Set.of("ADMIN"), 0, login).isEmpty(),
                "al tetto la sessione e' finita: si deve ripassare dal login");
    }

    @Test
    void senzaTettoContaSoloLInattivita() {
        // durata-massima a zero: il tetto e' spento e la sessione scorre finche'
        // l'utente lavora, anche per giorni.
        Instant treGiorniDopo = login.plus(Duration.ofDays(3));
        EmissioneToken e = emissione(treGiorniDopo, Duration.ZERO);
        assertEquals(treGiorniDopo.plus(INATTIVITA), e.scadenza(login));
    }

    @Test
    void ilCookieScadeInsiemeAlToken() {
        // Se il cookie durasse piu' del token, il browser continuerebbe a mandare un
        // token morto e l'utente vedrebbe un 401 invece della pagina di login.
        EmissioneToken e = emissione(login, TETTO);
        var sessione = new EmissioneToken.Sessione("finto", login.plus(Duration.ofMinutes(30)));
        assertEquals(INATTIVITA.getSeconds(), e.cookie(sessione).getMaxAge());
    }

    @Test
    void ilCookieCancellatoHaDurataZero() {
        var cancellato = emissione(login, TETTO).cookieCancellato();
        assertEquals(0, cancellato.getMaxAge());
        assertEquals("", cancellato.getValue());
        // Le difese del cookie valgono anche quando lo si cancella: senza Secure, il
        // browser lo rimanderebbe in chiaro su http.
        assertTrue(cancellato.isSecure());
        assertTrue(cancellato.isHttpOnly());
    }

    /**
     * Il token vero non si puo' firmare qui: servirebbe la chiave privata e quindi
     * Quarkus avviato. Ci limitiamo a verificare che, quando la sessione e' ancora
     * viva, il tentativo di firma parta davvero (fallendo sulla chiave mancante) e
     * non venga saltato per un calcolo di scadenza sbagliato.
     */
    @Test
    void finoAlTettoSiProvaAFirmare() {
        EmissioneToken e = emissione(login, TETTO);
        try {
            Optional<EmissioneToken.Sessione> sessione = e.emetti("admin", Set.of("ADMIN"), 3, login);
            assertTrue(sessione.isPresent());
        } catch (JwtSignatureException | IllegalStateException attesa) {
            // Nessuna chiave a disposizione fuori da Quarkus: va bene cosi', voleva
            // dire che ci stava provando.
        }
    }
}
