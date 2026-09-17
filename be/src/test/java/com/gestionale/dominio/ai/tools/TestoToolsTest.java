package com.gestionale.dominio.ai.tools;

import com.gestionale.dominio.repository.RiepilogoOre;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Il testo che tornano gli strumenti non lo legge una persona: lo legge il modello,
 * dentro al prompt. Per questo e' codice delicato in un modo insolito - se il formato
 * cambia non si rompe niente, nessun compilatore protesta e nessun test di integrazione
 * fallisce: l'assistente comincia semplicemente a rispondere peggio, e ce ne accorgiamo
 * quando qualcuno se ne lamenta.
 */
class TestoToolsTest {

    @Test
    void unElencoTroncatoLoDice() {
        // La query taglia a MAX_RIGHE. Senza l'avviso il modello presenterebbe le prime
        // 50 righe come se fossero tutte, ed e' il tipo di bugia che nessuno verifica.
        List<String> cinquanta = IntStream.range(0, TestoTools.MAX_RIGHE).mapToObj(i -> "riga " + i).toList();

        String testo = TestoTools.elenco(cinquanta, s -> s, "vuoto");

        assertTrue(testo.contains("elenco troncato ai primi " + TestoTools.MAX_RIGHE),
                "un elenco pieno deve avvisare che potrebbe non essere completo");
    }

    @Test
    void unElencoCortoNonDiceNienteDiTroncato() {
        String testo = TestoTools.elenco(List.of("uno", "due"), s -> s, "vuoto");

        assertEquals("- uno\n- due\n", testo);
        assertFalse(testo.contains("troncato"));
    }

    @Test
    void lElencoVuotoRestituisceLaFraseDiChiChiama() {
        // Frase e non elenco vuoto: il modello deve poter dire "non ce ne sono"
        // invece di trovarsi fra le mani una stringa vuota da interpretare.
        assertEquals("Nessun dipendente in anagrafica.",
                TestoTools.elenco(List.<String>of(), s -> s, "Nessun dipendente in anagrafica."));
    }

    @Test
    void ilRiepilogoChiudeConIlTotale() {
        String testo = TestoTools.riepilogo(List.of(
                new RiepilogoOre("Mario Rossi", new BigDecimal("8.00")),
                new RiepilogoOre("Luca Bianchi", new BigDecimal("4.50"))), "vuoto");

        assertTrue(testo.contains("Mario Rossi: 8"));
        assertTrue(testo.contains("Luca Bianchi: 4.5"));
        assertTrue(testo.endsWith("Totale complessivo: 12.5 ore"),
                "il totale lo deve calcolare qui, non il modello: sommare non e' il suo mestiere");
    }

    @Test
    void leOreSiLeggonoComeLeDirebbeUnaPersona() {
        // "8.00 ore" e "7.50 ore" il modello le ripete cosi' come sono, e suonano
        // come un importo in euro invece che come ore.
        assertEquals("8", TestoTools.ore(new BigDecimal("8.00")));
        assertEquals("7.5", TestoTools.ore(new BigDecimal("7.50")));
        assertEquals("0", TestoTools.ore(null));
    }

    @Test
    void unaDataMancanteNonDiventaNull() {
        // "null" dentro al prompt il modello lo riporta all'utente tale e quale.
        assertEquals("-", TestoTools.data(null));
        assertEquals("15/03/2026", TestoTools.data(LocalDate.of(2026, 3, 15)));
    }
}
