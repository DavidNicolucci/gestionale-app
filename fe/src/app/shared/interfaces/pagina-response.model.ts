/**
 * Risposta di una ricerca paginata del backend (`PaginaResponse<T>`).
 * Le pagine partono da 0, sia in richiesta che in risposta.
 */
export interface PaginaResponseModel<T> {
  risultati: T[];
  pagine: number;
  numeroDiPagina: number;
  totaleElementi: number;
}

/** Campi comuni a tutte le richieste di ricerca paginata (`RicercaPaginataRequest`). */
export interface RicercaPaginataRequestModel {
  numeroPagina?: number;
  righePerPagina?: number;
  ordinaPer?: string;
  ordinamento?: 'ASC' | 'DESC';
}
