/**
 * Body del rinnovo contratto (`RinnovoRequest`).
 *
 * Il tipo di contratto è facoltativo: si manda solo se il rinnovo lo cambia. Qui non
 * lo chiediamo — la finestra di rinnovo domanda solo la data nuova — ma il campo resta
 * nel modello perché è quello che il backend accetta.
 */
export interface RinnovoRequestModel {
  /** `yyyy-MM-dd`: è un giorno di calendario, non un istante. */
  dataScadenza: string;
  tipoContratto?: string;
}
