package com.gestionale.dominio.model.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Rinnovo del contratto: la nuova data di scadenza, e se serve il nuovo tipo.
 *
 * E' una richiesta a se' e non una PATCH qualsiasi perche' il rinnovo e' l'unico modo
 * di riportare in servizio un dipendente scaduto: tenerlo separato lo rende un gesto
 * esplicito, che si vede nei log e a cui si puo' dare un permesso suo.
 */
public class RinnovoRequest {

    // FutureOrPresent e non Future: rinnovare fino a oggi vuol dire "il contratto
    // finisce stasera", ed e' un caso legittimo. La scadenza e' inclusiva, l'ultimo
    // giorno si lavora ancora.
    @NotNull(message = "La nuova data di scadenza è obbligatoria")
    @FutureOrPresent(message = "La nuova scadenza non può essere nel passato")
    public LocalDate dataScadenza;

    // Facoltativo: si compila solo se il rinnovo cambia anche il tipo di contratto.
    public String tipoContratto;
}
