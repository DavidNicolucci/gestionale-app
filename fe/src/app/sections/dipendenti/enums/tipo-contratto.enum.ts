/**
 * Valori di `tipo_contratto`. Sul database la colonna è una stringa libera, ma i
 * dati in uso sono solo questi due: offrirli in una tendina evita che lo stesso
 * contratto finisca scritto in tre modi diversi e diventi infiltrabile.
 */
export enum TipoContratto {
  INDETERMINATO = 'INDETERMINATO',
  DETERMINATO = 'DETERMINATO',
}

/** Etichette a schermo: il valore salvato resta quello dell'enum. */
export const ETICHETTE_TIPO_CONTRATTO: Record<TipoContratto, string> = {
  [TipoContratto.INDETERMINATO]: 'Indeterminato',
  [TipoContratto.DETERMINATO]: 'Determinato',
};

/** Ordine della tendina: prima il caso più frequente. */
export const TIPI_CONTRATTO: readonly TipoContratto[] = [
  TipoContratto.INDETERMINATO,
  TipoContratto.DETERMINATO,
];
