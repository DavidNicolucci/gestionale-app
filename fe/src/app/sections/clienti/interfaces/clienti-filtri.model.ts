/** Stato dei campi di ricerca della pagina clienti. */
export interface ClientiFiltriModel {
  /** Casella di ricerca in alto. */
  termine: string;
  ragioneSociale: string;
  partitaIva: string;
}

export const FILTRI_CLIENTI_VUOTI: ClientiFiltriModel = {
  termine: '',
  ragioneSociale: '',
  partitaIva: '',
};
