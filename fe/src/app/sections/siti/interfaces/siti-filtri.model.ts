/** Stato dei campi di ricerca della pagina siti. */
export interface SitiFiltriModel {
  /** Casella di ricerca in alto: cerca sul nome, che è come si chiama un sito in elenco. */
  termine: string;
  nome: string;
  /**
   * Tendina dei clienti. `null` e non 0 quando non si filtra: lo zero sarebbe un
   * id come un altro, e non c'è modo di distinguerlo da "nessun cliente scelto".
   */
  clienteId: number | null;
}

export const FILTRI_SITI_VUOTI: SitiFiltriModel = {
  termine: '',
  nome: '',
  clienteId: null,
};
