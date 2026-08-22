/**
 * Testi della finestra di conferma. Obbligatorio solo il titolo: gli altri campi
 * hanno un default, così per una conferma banale basta passare la domanda.
 */
export interface ConfermaDialogDataInterface {
  /** La domanda, breve: "Eliminare il cliente?". */
  titolo: string;
  /** Riga sotto al titolo: cosa succede davvero se si conferma. */
  messaggio?: string;
  /** Etichetta del pulsante che va avanti. Default: "Conferma". */
  conferma?: string;
  /** Etichetta del pulsante che si tira indietro. Default: "Annulla". */
  annulla?: string;
}
