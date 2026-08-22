/** Stato dei campi di ricerca della pagina dipendenti. */
export interface DipendentiFiltriModel {
  /** Casella di ricerca in alto: cerca sul cognome, che è come si chiama una persona in elenco. */
  termine: string;
  nome: string;
  cognome: string;
  codiceFiscale: string;
}

export const FILTRI_DIPENDENTI_VUOTI: DipendentiFiltriModel = {
  termine: '',
  nome: '',
  cognome: '',
  codiceFiscale: '',
};
