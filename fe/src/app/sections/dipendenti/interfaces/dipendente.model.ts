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
}
