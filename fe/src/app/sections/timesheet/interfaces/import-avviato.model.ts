/**
 * Risposta 202 dell'upload: il backend conferma solo di aver preso in carico il
 * file. L'import vero avviene dopo, in coda, quindi qui non c'è il numero di
 * righe importate né l'esito.
 */
export class ImportAvviatoModel {
  messaggio!: string;
}
