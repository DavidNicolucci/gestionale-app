package com.gestionale.dominio.imports;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Il classificatore decide se un blocco di righe viene riprovato o se il file finisce
 * subito in coda di scarto. E' logica che si rompe in silenzio: se un giorno dicesse
 * "transitorio" a un vincolo violato, ogni import sbagliato costerebbe tre tentativi e
 * un'attesa prima di fallire lo stesso; se dicesse "definitivo" a una connessione
 * caduta, tornerebbe il comportamento che questi cambiamenti volevano togliere.
 */
class ErroriTransitoriTest {

    // ---------- si riprova ----------

    @Test
    void laConnessioneCadutaSiRiprova() {
        assertTrue(ErroriTransitori.transitorio(
                new SQLTransientConnectionException("connection is closed")));
    }

    @Test
    void ilDeadlockSiRiprova() {
        // 1205 = "vittima del deadlock": rifare la transazione e' esattamente la cura
        // prescritta da SQL Server.
        assertTrue(ErroriTransitori.transitorio(
                new SQLException("deadlock victim", "40001", 1205)));
    }

    @Test
    void ilLockTimeoutSiRiprova() {
        assertTrue(ErroriTransitori.transitorio(
                new SQLException("lock request time out", "HY000", 1222)));
    }

    @Test
    void loSqlStateDiConnessioneSiRiprova() {
        // Classe 08 = connection exception, qualunque sia il codice del fornitore.
        assertTrue(ErroriTransitori.transitorio(
                new SQLException("connessione persa", "08S01", 0)));
    }

    @Test
    void siGuardaAncheDentroLeEccezioniAnnidate() {
        // E' il caso reale: quello che arriva al consumer non e' mai la SQLException
        // nuda, ma un involucro di Hibernate dentro un involucro di JTA.
        Throwable annidata = new RuntimeException("transazione fallita",
                new IllegalStateException("errore JDBC",
                        new SQLException("deadlock victim", "40001", 1205)));
        assertTrue(ErroriTransitori.transitorio(annidata));
    }

    // ---------- non si riprova ----------

    @Test
    void ilVincoloViolatoNonSiRiprova() {
        // Viene dal database ma e' definitivo quanto un file rovinato: la stessa riga
        // dara' lo stesso errore fra due secondi. SQLState classe 23 = integrity
        // constraint violation, 2601 = chiave duplicata su indice unico.
        assertFalse(ErroriTransitori.transitorio(
                new SQLException("duplicate key row in object 'dbo.timesheet'", "23000", 2601)));
    }

    @Test
    void ilFileRovinatoNonSiRiprova() {
        assertFalse(ErroriTransitori.transitorio(
                new IOException("Your InputStream was neither an OLE2 stream, nor an OOXML stream")));
    }

    @Test
    void unErroreSconosciutoNonSiRiprova() {
        // La regola e' "transitorio solo se riconosciuto tale": nel dubbio si va in DLQ
        // subito, perche' riprovare tutto trasformerebbe ogni bug in tre tentativi.
        assertFalse(ErroriTransitori.transitorio(new NullPointerException()));
    }

    @Test
    void nullNonEsplode() {
        assertFalse(ErroriTransitori.transitorio(null));
    }

    @Test
    void unaCatenaCircolareNonMandaInLoop() {
        // Una causa che punta a se stessa farebbe girare a vuoto il ciclo che scorre la
        // catena: qui si verifica che il metodo torni comunque.
        Exception e = new Exception("giro") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertFalse(ErroriTransitori.transitorio(e));
    }

    // ---------- il motivo scritto sul registro ----------

    @Test
    void ilMotivoRiportaLaCatenaDelleCause() {
        String motivo = ErroriTransitori.descrivi(
                new RuntimeException("import fallito",
                        new SQLException("deadlock victim", "40001", 1205)));

        assertTrue(motivo.contains("RuntimeException: import fallito"));
        assertTrue(motivo.contains("SQLException: deadlock victim"));
        assertTrue(motivo.contains("<-"), "le cause vanno separate, non concatenate a caso");
    }

    @Test
    void ilMotivoStaDentroLaColonna() {
        // La colonna e' NVARCHAR(2000): su SQL Server un valore piu' lungo non viene
        // troncato, fa fallire la INSERT. E il fallimento sarebbe proprio nella scrittura
        // dell'esito, cioe' l'unico posto dove non ce lo possiamo permettere.
        String lunghissimo = "x".repeat(10_000);
        String motivo = ErroriTransitori.descrivi(new RuntimeException(lunghissimo));

        assertTrue(motivo.length() <= 2000, "era lungo " + motivo.length());
        assertTrue(motivo.endsWith("..."));
    }

    @Test
    void ilMotivoReggeUnMessaggioAssente() {
        assertEquals("NullPointerException", ErroriTransitori.descrivi(new NullPointerException()));
    }

    @Test
    void ilMotivoStaSuUnaRigaSola() {
        // Finisce in una colonna e in un elenco: un messaggio a capo spezzerebbe la
        // riga a video.
        String motivo = ErroriTransitori.descrivi(new RuntimeException("prima\nseconda"));
        assertFalse(motivo.contains("\n"));
    }
}
