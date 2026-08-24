import { StatoDipendente } from '../enums/stato-dipendente.enum';

/** Dipendente restituito dal backend (`DipendenteResponse`). */
export class DipendenteModel {
  id!: number;
  nome!: string;
  cognome!: string;
  codiceFiscale!: string;
  /** Date `LocalDate`: arrivano come `yyyy-MM-dd`, non come istanti. */
  dataNascita!: string;
  nazionalita!: string;
  tipoContratto!: string;
  dataAssunzione!: string;
  /** Vuota quando il contratto è a tempo indeterminato: non scade. */
  dataScadenza?: string;
  /**
   * Calcolato dal backend al momento della risposta: dipende da che giorno è oggi.
   * Non ricavarlo da `dataScadenza` qui — vale la stessa regola che decide chi si
   * può usare, e deve esistere in un posto solo.
   */
  stato!: StatoDipendente;
}
