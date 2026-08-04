/**
 * Stato del paginatore a schermo. Le pagine partono da 0, come nel backend
 * (`RicercaPaginataRequest`), così non c'è nessuna conversione da ricordare.
 */
export interface PaginazioneModel {
  numeroPagina: number;
  righePerPagina: number;
}

/** Stesso default del backend: se non mandiamo nulla lui userebbe comunque 10. */
export const RIGHE_PER_PAGINA_DEFAULT = 10;

/** Il backend rifiuta pagine più grandi di 100, quindi qui non le offriamo. */
export const OPZIONI_RIGHE_PER_PAGINA = [5, 10, 25, 50];

export const PAGINAZIONE_INIZIALE: PaginazioneModel = {
  numeroPagina: 0,
  righePerPagina: RIGHE_PER_PAGINA_DEFAULT,
};
