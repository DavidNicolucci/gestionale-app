/**
 * Stato di un dipendente, calcolato dal backend (`StatoDipendente` lato Java).
 *
 * Non è una colonna del database: si ricava dal flag di eliminazione e dal confronto
 * fra la data di scadenza e oggi. Qui non lo ricalcoliamo — arriva già pronto nella
 * risposta — perché è la stessa regola che decide chi si può usare: se il frontend
 * se la riscrivesse, prima o poi le due versioni direbbero cose diverse.
 */
export enum StatoDipendente {
  ATTIVO = 'ATTIVO',
  /** Ancora in servizio, ma il contratto finisce entro la finestra di preavviso. */
  IN_SCADENZA = 'IN_SCADENZA',
  /** Contratto finito: non si può usare né modificare finché non viene rinnovato. */
  SCADUTO = 'SCADUTO',
  /** Cancellato logicamente: fuori dagli elenchi, ma i suoi timesheet restano. */
  ELIMINATO = 'ELIMINATO',
}

/** Etichette a schermo: il valore che viaggia resta quello dell'enum. */
export const ETICHETTE_STATO_DIPENDENTE: Record<StatoDipendente, string> = {
  [StatoDipendente.ATTIVO]: 'Attivo',
  [StatoDipendente.IN_SCADENZA]: 'In scadenza',
  [StatoDipendente.SCADUTO]: 'Scaduto',
  [StatoDipendente.ELIMINATO]: 'Eliminato',
};

/**
 * Suffisso della classe CSS della pastiglia. Scritto a mano e non ricavato dal valore
 * con un `toLowerCase()`: così i quattro nomi che compaiono nello scss esistono anche
 * qui, e rinominarne uno senza toccare l'altro file diventa un errore di TypeScript.
 */
export const CLASSI_STATO_DIPENDENTE: Record<StatoDipendente, string> = {
  [StatoDipendente.ATTIVO]: 'attivo',
  [StatoDipendente.IN_SCADENZA]: 'in-scadenza',
  [StatoDipendente.SCADUTO]: 'scaduto',
  [StatoDipendente.ELIMINATO]: 'eliminato',
};

/**
 * Se il dipendente si può ancora usare: consuntivargli ore, modificarlo, sceglierlo
 * in una tendina. `IN_SCADENZA` è a tutti gli effetti un attivo — è solo un avviso.
 */
export function utilizzabile(stato: StatoDipendente): boolean {
  return stato === StatoDipendente.ATTIVO || stato === StatoDipendente.IN_SCADENZA;
}
