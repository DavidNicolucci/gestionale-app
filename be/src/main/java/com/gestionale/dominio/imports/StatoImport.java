package com.gestionale.dominio.imports;

/**
 * Dove si trova un file caricato, dal momento dell'upload in poi.
 *
 * I nomi finiscono tali e quali sulla colonna import_job.stato (mappata come stringa e
 * non come numero: un ordinale sul database diventa illeggibile appena qualcuno
 * riordina l'enum). Il vincolo ck_import_job_stato nello schema elenca gli stessi
 * cinque valori: aggiungerne uno qui vuol dire aggiornare anche quello.
 */
public enum StatoImport {

    /** In coda, il consumer non l'ha ancora preso. */
    ACCODATO,

    /** Il consumer ci sta lavorando. Se resta qui a lungo, qualcosa si e' fermato. */
    IN_CORSO,

    /** Finito bene: il file e' stato cancellato dal disco. */
    COMPLETATO,

    /**
     * Finito male. Il messaggio e' in DLQ (o ci e' gia' passato) e il file resta su
     * disco: e' quello che rende possibile il rilancio dall'endpoint admin.
     */
    FALLITO,

    /**
     * Un ADMIN ha deciso che non si ritenta: file cancellato, riga tenuta come traccia.
     * Serve a non far diventare l'elenco dei falliti un cimitero come la DLQ.
     */
    ABBANDONATO;

    /**
     * Lo stato su cui il rilancio e' sempre lecito. Sugli altri non e' vietato ma va
     * forzato: ACCODATO e IN_CORSO possono essere ancora vivi (rilanciarli vorrebbe
     * dire due consumer sullo stesso file), COMPLETATO e ABBANDONATO non hanno piu' il
     * file su disco.
     */
    public boolean ritentabile() {
        return this == FALLITO;
    }

    /** Gli stati che tengono occupato quel contenuto: un file con lo stesso hash non si ricarica. */
    public boolean occupaIlContenuto() {
        return this == ACCODATO || this == IN_CORSO || this == COMPLETATO;
    }
}
