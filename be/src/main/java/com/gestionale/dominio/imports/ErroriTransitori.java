package com.gestionale.dominio.imports;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientException;
import java.util.Set;

/**
 * Distingue l'errore che passa da solo da quello che non passera' mai.
 *
 * Serve a decidere se riprovare. La distinzione non e' "e' un errore del database?":
 * un vincolo di unicita' violato viene dal database ed e' definitivo quanto un file
 * illeggibile, riprovarlo vuol dire solo aspettare tre volte per avere tre volte lo
 * stesso errore. Quello che si riprova e' l'errore di CIRCOSTANZA: la connessione
 * caduta per due secondi, il deadlock, il lock timeout. Quelli, dopo qualche secondo,
 * spesso non ci sono piu'.
 *
 * La regola e' "transitorio solo se riconosciuto tale", non il contrario: un errore che
 * non sappiamo classificare viene trattato come definitivo e va in DLQ subito. E' la
 * scelta prudente, perche' l'alternativa (riprovare tutto) trasforma ogni bug in tre
 * tentativi e un ritardo.
 */
final class ErroriTransitori {

    private ErroriTransitori() { }

    // Codici di errore di SQL Server che descrivono una circostanza, non un dato sbagliato.
    private static final Set<Integer> CODICI_SQL_SERVER = Set.of(
            1205,   // deadlock: la transazione e' stata scelta come vittima, rifarla e' esattamente la cura
            1222,   // lock request time out
            -2,     // query timeout lato driver
            233,    // connessione chiusa dal server mentre la si usava
            10053,  // connessione interrotta dal software dell'host
            10054,  // connessione azzerata dal peer
            10060,  // connessione non riuscita entro il tempo massimo
            40197,  // il servizio ha avuto un problema elaborando la richiesta (riprovare)
            40501,  // servizio occupato
            40613,  // database non disponibile al momento
            4060,   // impossibile aprire il database (spesso: si sta ancora avviando)
            49918, 49919, 49920  // troppe richieste in corso, riprovare
    );

    // Classi di SQLState: "08" = connessione, "40" = transazione annullata dal server.
    // Volutamente NON c'e' "23" (violazione di vincolo): quello e' un dato sbagliato.
    private static final Set<String> CLASSI_SQL_STATE = Set.of("08", "40");

    private static final Set<String> CLASSI_HIBERNATE = Set.of(
            "org.hibernate.exception.JDBCConnectionException",
            "org.hibernate.exception.LockAcquisitionException",
            "org.hibernate.exception.LockTimeoutException",
            "org.hibernate.TransactionException",
            "org.hibernate.PessimisticLockException",
            "org.hibernate.QueryTimeoutException",
            "jakarta.persistence.LockTimeoutException",
            "jakarta.persistence.PessimisticLockException",
            "jakarta.persistence.QueryTimeoutException"
    );

    /** Vale la pena riprovare fra qualche secondo? */
    static boolean transitorio(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLTransientException
                    || t instanceof SQLRecoverableException
                    || t instanceof SQLTimeoutException) {
                return true;
            }
            if (CLASSI_HIBERNATE.contains(t.getClass().getName())) {
                return true;
            }
            if (t instanceof SQLException sql && transitorioSql(sql)) {
                return true;
            }
            // Una catena con un ciclo (e.getCause() == e) farebbe girare a vuoto.
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static boolean transitorioSql(SQLException sql) {
        if (CODICI_SQL_SERVER.contains(sql.getErrorCode())) {
            return true;
        }
        String stato = sql.getSQLState();
        return stato != null && stato.length() >= 2 && CLASSI_SQL_STATE.contains(stato.substring(0, 2));
    }

    /**
     * Il motivo in una riga, da scrivere sulla colonna errore del job: e' quello che
     * legge l'ADMIN nell'elenco, quindi deve dire cosa e' successo senza obbligarlo ad
     * aprire i log. Lo stack trace resta nei log, qui va la sostanza.
     */
    static String descrivi(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null && sb.length() < 1500; t = t.getCause()) {
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(t.getClass().getSimpleName());
            if (t.getMessage() != null) {
                sb.append(": ").append(t.getMessage().replace('\n', ' ').trim());
            }
            if (t.getCause() == t) {
                break;
            }
        }
        // La colonna e' NVARCHAR(2000): tagliamo prima noi, con i puntini, invece di
        // farci troncare dal database (che su SQL Server e' un errore, non un taglio).
        String testo = sb.toString();
        return testo.length() <= 1997 ? testo : testo.substring(0, 1997) + "...";
    }
}
