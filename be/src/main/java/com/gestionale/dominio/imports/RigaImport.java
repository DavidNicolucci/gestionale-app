package com.gestionale.dominio.imports;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una riga del foglio Excel, gia' letta e convertita, non ancora salvata.
 *
 * Esiste perche' la lettura del file e la scrittura sul database sono state separate:
 * il foglio si legge tutto fuori da qualunque transazione, poi le righe si salvano a
 * blocchi. Prima le due cose erano la stessa cosa, e leggere 5.000 righe teneva
 * occupata una connessione del pool per tutta la durata della lettura.
 *
 * numeroRiga e' quello del foglio (parte da 1, la 0 e' l'intestazione): serve nei
 * messaggi di errore, perche' chi deve correggere il file ragiona per numero di riga,
 * e serve a ImportJob.ultimaRiga per sapere da dove ripartire.
 */
public record RigaImport(int numeroRiga,
                         String codiceFiscale,
                         String nomeSito,
                         LocalDate data,
                         BigDecimal ore) {
}
