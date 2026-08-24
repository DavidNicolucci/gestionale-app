package com.gestionale.dominio.model.dto;

import com.gestionale.dominio.model.entity.Dipendente;

/**
 * Riepilogo per l'avviso sopra la tabella dipendenti.
 *
 * Porta con se' anche la finestra di preavviso, invece di lasciare che il frontend
 * sappia che sono 15 giorni: il numero vive sull'entity, e questo e' il modo di non
 * riscriverlo da nessun'altra parte. Il giorno che diventa 30, il messaggio a video
 * cambia da solo.
 */
public class ScadenzeResponse {

    /** Quanti contratti finiscono da oggi entro la finestra di preavviso. */
    public long inScadenza;

    /** Ampiezza della finestra, in giorni. */
    public int giorniPreavviso;

    public static ScadenzeResponse di(long inScadenza) {
        ScadenzeResponse r = new ScadenzeResponse();
        r.inScadenza = inScadenza;
        r.giorniPreavviso = Dipendente.GIORNI_PREAVVISO;
        return r;
    }
}
