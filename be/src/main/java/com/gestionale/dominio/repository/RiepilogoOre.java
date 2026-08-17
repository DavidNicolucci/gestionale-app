package com.gestionale.dominio.repository;

import java.math.BigDecimal;

/**
 * Riga di un raggruppamento di ore: a cosa si riferiscono e quante sono.
 *
 * L'etichetta e' generica di proposito - a seconda della query e' un dipendente,
 * un sito o un cliente - perche' e' il risultato di una somma per gruppo, non
 * un'entita' del dominio.
 */
public record RiepilogoOre(String etichetta, BigDecimal ore) {
}
