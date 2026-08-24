/**
 * Riepilogo delle scadenze imminenti (`ScadenzeResponse`), per l'avviso in cima
 * alla pagina.
 *
 * `giorniPreavviso` arriva dal backend invece di essere scritto qui: la finestra è
 * decisa là (`Dipendente.GIORNI_PREAVVISO`), e riscriverla anche nel frontend vorrebbe
 * dire ritrovarsi un giorno con il conteggio fatto su una finestra e la frase che ne
 * annuncia un'altra.
 */
export interface ScadenzeModel {
  inScadenza: number;
  giorniPreavviso: number;
}
