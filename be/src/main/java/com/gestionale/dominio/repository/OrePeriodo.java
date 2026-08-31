package com.gestionale.dominio.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ore lavorate in un intervallo, con i giorni in cui si e' lavorato davvero.
 *
 * Il primo e l'ultimo giorno non sono gli estremi chiesti dall'utente ma quelli
 * delle righe trovate: a chi domanda "quante ore ad agosto" interessa sapere che
 * ha lavorato dal 24 al 27, non che agosto va dall'1 al 31.
 *
 * Quando nel periodo non c'e' nessuna riga la query di aggregazione torna comunque
 * una riga, con tutti i campi a null: e' il caso che {@link #vuoto()} riconosce.
 */
public record OrePeriodo(BigDecimal ore, LocalDate primoGiorno, LocalDate ultimoGiorno) {

    public boolean vuoto() {
        return primoGiorno == null;
    }
}
