import {MatPaginatorIntl} from '@angular/material/paginator';

/**
 * Etichette del paginatore in italiano: di serie Material le scrive in inglese
 * ("Items per page", "1 – 10 of 25") e stonano nel resto della pagina.
 */
export function paginatoreItaliano(): MatPaginatorIntl {
  const etichette = new MatPaginatorIntl();

  etichette.itemsPerPageLabel = 'Righe per pagina';
  etichette.firstPageLabel = 'Prima pagina';
  etichette.previousPageLabel = 'Pagina precedente';
  etichette.nextPageLabel = 'Pagina successiva';
  etichette.lastPageLabel = 'Ultima pagina';

  etichette.getRangeLabel = (numeroPagina, righePerPagina, totale) => {
    if (totale === 0 || righePerPagina === 0) {
      return `0 di ${totale}`;
    }

    const inizio = numeroPagina * righePerPagina;
    // L'ultima pagina è quasi sempre incompleta: senza il min si annuncerebbero
    // più righe di quante ne esistano.
    const fine = Math.min(inizio + righePerPagina, totale);

    return `${inizio + 1} – ${fine} di ${totale}`;
  };

  return etichette;
}
