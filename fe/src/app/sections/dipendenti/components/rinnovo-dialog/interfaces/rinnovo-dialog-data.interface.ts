/** Quello che la finestra di rinnovo deve sapere sul dipendente che sta rinnovando. */
export interface RinnovoDialogDataInterface {
  /** "Rossi Mario": serve solo a far vedere su chi si sta agendo. */
  nominativo: string;
  /** Scadenza attuale (`yyyy-MM-dd`), mostrata come riferimento. */
  dataScadenzaAttuale?: string;
}
