package com.gestionale.dominio.ai.tools;

import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.model.entity.Sito;
import com.gestionale.dominio.model.entity.Timesheet;
import com.gestionale.dominio.repository.OrePeriodo;
import com.gestionale.dominio.repository.RiepilogoOre;
import com.gestionale.dominio.repository.TimesheetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le ore sono il pezzo dove una risposta sbagliata costa davvero: qualcuno le
 * fattura. Qui si prova quello che nessun compilatore puo' proteggere, cioe' che il
 * testo dato in pasto al modello dica esattamente quello che intendiamo.
 */
class TimesheetToolsTest {

    private DipendenteRepositoryFinto dipendenti;
    private TimesheetRepositoryFinto timesheet;
    private TimesheetTools tools;

    @BeforeEach
    void preparaGliStrumenti() {
        dipendenti = new DipendenteRepositoryFinto();
        timesheet = new TimesheetRepositoryFinto();
        tools = new TimesheetTools();
        tools.dipendenti = dipendenti;
        tools.timesheet = timesheet;
    }

    @Test
    void leDateRiportateSonoQuelleLavorateDavvero() {
        // E' la trappola di questo strumento. A chi chiede "quante ore ad agosto"
        // interessa sapere che ha lavorato dal 24 al 27: rispondere "dall'1 al 31"
        // farebbe sembrare vuoto il resto del mese quando magari la persona era
        // assunta a meta' agosto. Il prompt lo dice al modello, ma solo perche' i
        // due giorni veri sono davvero nel testo.
        dipendenti.conDipendente(mario());
        timesheet.periodo = new OrePeriodo(new BigDecimal("32.00"),
                LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 27));

        String testo = tools.oreLavorate("Mario Rossi",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals("Mario Rossi ha lavorato 32 ore tra il 24/08/2026 e il 27/08/2026.", testo);
    }

    @Test
    void senzaOreSiRipeteIlPeriodoChiesto() {
        // Qui il primo e l'ultimo giorno lavorato non esistono: le uniche date
        // sensate da rimandare indietro sono quelle della domanda.
        dipendenti.conDipendente(mario());
        timesheet.periodo = new OrePeriodo(BigDecimal.ZERO, null, null);

        String testo = tools.oreLavorate("Mario Rossi",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals("Mario Rossi non ha ore registrate tra il 01/08/2026 e il 31/08/2026.", testo);
    }

    @Test
    void suUnDipendenteCheNonEsisteNonSiInventaUnTotale() {
        String testo = tools.oreLavorate("Chi Non Ce",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals("Nessun dipendente trovato con nome 'Chi Non Ce'.", testo);
    }

    @Test
    void ilRiepilogoArrivaOrdinatoConIlSuoTotale() {
        timesheet.riepilogo = List.of(
                new RiepilogoOre("Mario Rossi", new BigDecimal("40.00")),
                new RiepilogoOre("Luca Bianchi", new BigDecimal("12.50")));

        String testo = tools.riepilogoOrePerDipendente(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertTrue(testo.contains("- Mario Rossi: 40\n"), testo);
        assertTrue(testo.endsWith("Totale complessivo: 52.5 ore"), testo);
    }

    @Test
    void unNumeroAssurdoDiRegistrazioniVieneRiportatoNeiLimiti() {
        // Il parametro lo sceglie il modello leggendo la domanda: puo' arrivare
        // "1000" da "mostrami tutto" o un numero negativo da un fraintendimento.
        tools.ultimeRegistrazioni(1000);
        assertEquals(TestoTools.MAX_RIGHE, timesheet.limiteRicevuto);

        tools.ultimeRegistrazioni(-3);
        assertEquals(1, timesheet.limiteRicevuto);
    }

    @Test
    void leUltimeRegistrazioniHannoDipendenteSitoDataEOre() {
        timesheet.registrazioni = List.of(registrazione());

        String testo = tools.ultimeRegistrazioni(10);

        assertEquals("- 27/08/2026 - Mario Rossi - sito Cantiere Via Roma - 7.5 ore\n", testo);
    }

    // ---------- dati di prova ----------

    private static Dipendente mario() {
        Dipendente d = new Dipendente();
        d.id = 1L;
        d.nome = "Mario";
        d.cognome = "Rossi";
        d.codiceFiscale = "RSSMRA85E20H501Z";
        d.dataNascita = LocalDate.of(1985, 5, 20);
        d.nazionalita = "Italiana";
        d.tipoContratto = "Tempo indeterminato";
        d.dataAssunzione = LocalDate.of(2024, 1, 1);
        return d;
    }

    private static Timesheet registrazione() {
        Sito sito = new Sito();
        sito.nome = "Cantiere Via Roma";

        Timesheet t = new Timesheet();
        t.dipendente = mario();
        t.sito = sito;
        t.dataLavoro = LocalDate.of(2026, 8, 27);
        t.oreLavorate = new BigDecimal("7.50");
        return t;
    }

    /** Come {@link DipendenteRepositoryFinto}, ma per le ore: valori decisi dal test. */
    private static class TimesheetRepositoryFinto extends TimesheetRepository {

        OrePeriodo periodo = new OrePeriodo(BigDecimal.ZERO, null, null);
        List<RiepilogoOre> riepilogo = List.of();
        List<Timesheet> registrazioni = List.of();

        /** Quante registrazioni sono state chieste davvero, dopo il taglio dello strumento. */
        int limiteRicevuto;

        @Override
        public OrePeriodo oreLavoratePeriodo(Long dipendenteId, LocalDate da, LocalDate a) {
            return periodo;
        }

        @Override
        public List<RiepilogoOre> orePerDipendente(LocalDate da, LocalDate a) {
            return riepilogo;
        }

        @Override
        public List<Timesheet> ultimeRegistrazioni(int quante) {
            limiteRicevuto = quante;
            return registrazioni;
        }
    }
}
