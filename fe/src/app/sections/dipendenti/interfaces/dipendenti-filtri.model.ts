/** Stato dei campi di ricerca della pagina dipendenti. */
export interface DipendentiFiltriModel {
  /** Casella di ricerca in alto: cerca sul cognome, che è come si chiama una persona in elenco. */
  termine: string;
  nome: string;
  cognome: string;
  codiceFiscale: string;
  /**
   * Interruttore "Mostra eliminati". A differenza degli altri campi non aspetta
   * "Applica filtri": un interruttore che resta acceso senza che cambi niente si
   * legge come rotto. La pagina lo applica appena viene mosso.
   */
  includiEliminati: boolean;
}

export const FILTRI_DIPENDENTI_VUOTI: DipendentiFiltriModel = {
  termine: '',
  nome: '',
  cognome: '',
  codiceFiscale: '',
  includiEliminati: false,
};
