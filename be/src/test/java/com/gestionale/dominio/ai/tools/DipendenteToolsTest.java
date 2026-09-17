package com.gestionale.dominio.ai.tools;

import com.gestionale.dominio.model.entity.Dipendente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gli strumenti dell'assistente, provati per quello che sono davvero: pezzi di testo
 * che finiscono nel prompt di un modello.
 *
 * Il caso che conta piu' di tutti e' la coda "Non elencati: ...". Gli elenchi mostrano
 * solo chi e' in servizio, ma a decidere il filtro e' il modello che legge la domanda,
 * e puo' sbagliarsi: la coda e' la rete di sicurezza che fa arrivare comunque
 * all'utente l'informazione che esistono altri dipendenti. Se sparisse, nessuno se ne
 * accorgerebbe - l'assistente continuerebbe a rispondere, solo in modo incompleto.
 */
class DipendenteToolsTest {

    private DipendenteRepositoryFinto repository;
    private DipendenteTools tools;

    private static final LocalDate OGGI = LocalDate.now();

    @BeforeEach
    void preparaGliStrumenti() {
        repository = new DipendenteRepositoryFinto();
        tools = new DipendenteTools();
        tools.dipendenti = repository;
    }

    // ---------- elencaDipendenti ----------

    @Test
    void lElencoDichiaraQuantiNeHaLasciatiFuori() {
        repository.conDipendente(attivo("Mario", "Rossi"))
                .conDipendente(scaduto("Luca", "Bianchi"))
                .conDipendente(scaduto("Anna", "Verdi"))
                .conDipendente(eliminato("Carlo", "Neri"));

        String testo = tools.elencaDipendenti(false);

        assertTrue(testo.contains("Dipendenti attivi: 1."), testo);
        assertTrue(testo.contains("Mario Rossi"), testo);
        assertFalse(testo.contains("Luca Bianchi"), "gli scaduti non vanno elencati se non richiesti");
        assertTrue(testo.contains("Non elencati: 2 con il contratto scaduto, 1 eliminato."), testo);
        assertTrue(testo.contains("includiNonAttivi=true"),
                "va detto al modello come chiederli, o l'informazione non gli serve a niente");
    }

    @Test
    void senzaEsclusiNonCEUnaCodaDaSpiegare() {
        repository.conDipendente(attivo("Mario", "Rossi"));

        String testo = tools.elencaDipendenti(false);

        assertFalse(testo.contains("Non elencati"),
                "una coda che dice 'non elencati: nessuno' sarebbe solo rumore nel prompt");
    }

    @Test
    void ilPluraleDegliEliminatiSegueILoroNumero() {
        repository.conDipendente(attivo("Mario", "Rossi"))
                .conDipendente(eliminato("Carlo", "Neri"))
                .conDipendente(eliminato("Sara", "Gialli"));

        assertTrue(tools.elencaDipendenti(false).contains("Non elencati: 2 eliminati."),
                "'2 eliminato' e' il tipo di sgrammaticatura che il modello ricopia nella risposta");
    }

    @Test
    void chiediendoliTuttiCambiaLIntestazioneESparisceLaCoda() {
        repository.conDipendente(attivo("Mario", "Rossi"))
                .conDipendente(scaduto("Luca", "Bianchi"));

        String testo = tools.elencaDipendenti(true);

        assertTrue(testo.contains("compresi scaduti ed eliminati: 2."), testo);
        assertTrue(testo.contains("Luca Bianchi"), testo);
        assertFalse(testo.contains("Non elencati"), "sono gia' tutti nell'elenco");
    }

    @Test
    void lAnagraficaVuotaLoDiceConUnaFrase() {
        assertTrue(tools.elencaDipendenti(false).contains("Nessun dipendente in anagrafica."));
    }

    @Test
    void loStatoCompareSoloSuChiNonELInServizioNormale() {
        // Ripetere "in servizio" su ogni riga costerebbe token senza dire niente;
        // ometterlo su uno scaduto lo farebbe passare per uno come gli altri.
        repository.conDipendente(attivo("Mario", "Rossi"))
                .conDipendente(scaduto("Luca", "Bianchi"));

        String testo = tools.elencaDipendenti(true);

        assertTrue(testo.contains("Mario Rossi (CF: CF-Mario-Rossi)\n"), testo);
        assertTrue(testo.contains("Luca Bianchi (CF: CF-Luca-Bianchi) - NON utilizzabile: contratto scaduto"), testo);
    }

    // ---------- cercaDipendenti ----------

    @Test
    void laRicercaNascondeINonAttiviMaDiceQuantiSono() {
        repository.conDipendente(attivo("Mario", "Rossi"))
                .conDipendente(scaduto("Luigi", "Rossi"));

        String testo = tools.cercaDipendenti("rossi", false);

        assertTrue(testo.contains("Mario Rossi"), testo);
        assertFalse(testo.contains("Luigi Rossi"), testo);
        assertTrue(testo.contains("Altri 1 risultati non elencati"), testo);
    }

    @Test
    void laRicercaSenzaRisultatiNonInventaNiente() {
        assertEquals("Nessun dipendente attivo trovato per 'rossi'.",
                tools.cercaDipendenti("rossi", false));
    }

    // ---------- dettagliDipendente ----------

    @Test
    void laSchedaDiUnoCheNonCEIndirizzaAllaRicerca() {
        // Frase e non eccezione: il modello deve poter dire "non l'ho trovato" e
        // riprovare da solo con lo strumento giusto, invece di inventarsi una scheda.
        String testo = tools.dettagliDipendente("Chi Non Ce");

        assertTrue(testo.contains("Nessun dipendente trovato"), testo);
        assertTrue(testo.contains("cercaDipendenti"), testo);
    }

    @Test
    void laSchedaRispondeAncheSuUnContrattoScaduto() {
        // E' proprio quando il contratto e' finito che qualcuno chiede quando e' scaduto.
        repository.conDipendente(scaduto("Luca", "Bianchi"));

        String testo = tools.dettagliDipendente("Luca Bianchi");

        assertTrue(testo.contains("Luca Bianchi"), testo);
        assertTrue(testo.contains("NON utilizzabile: contratto scaduto"), testo);
    }

    @Test
    void ilTempoIndeterminatoNonHaUnaScadenzaDaMostrare() {
        repository.conDipendente(attivo("Mario", "Rossi"));

        assertTrue(tools.dettagliDipendente("Mario Rossi")
                .contains("Scadenza contratto: nessuna (tempo indeterminato)"));
    }

    // ---------- dipendenti di prova ----------

    private static Dipendente attivo(String nome, String cognome) {
        return dipendente(nome, cognome, null, false);
    }

    private static Dipendente scaduto(String nome, String cognome) {
        return dipendente(nome, cognome, OGGI.minusDays(10), false);
    }

    private static Dipendente eliminato(String nome, String cognome) {
        return dipendente(nome, cognome, null, true);
    }

    private static Dipendente dipendente(String nome, String cognome, LocalDate scadenza, boolean eliminato) {
        Dipendente d = new Dipendente();
        d.nome = nome;
        d.cognome = cognome;
        d.codiceFiscale = "CF-" + nome + "-" + cognome;
        d.dataNascita = LocalDate.of(1985, 5, 20);
        d.nazionalita = "Italiana";
        d.tipoContratto = scadenza == null ? "Tempo indeterminato" : "Tempo determinato";
        d.dataAssunzione = OGGI.minusYears(2);
        d.dataScadenza = scadenza;
        d.eliminato = eliminato;
        return d;
    }
}
