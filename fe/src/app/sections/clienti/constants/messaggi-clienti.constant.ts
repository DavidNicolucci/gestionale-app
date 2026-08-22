/**
 * Testi delle conferme della sezione clienti. Stanno qui e non nel componente
 * perché non sono logica di pagina ma contenuto, e la stessa domanda servirà
 * anche al dettaglio cliente quando ci sarà.
 *
 * Non in `shared/`: il messaggio parla dei siti collegati, cioè di com'è fatto il
 * dominio cliente. In `shared/` ci va quello che vale per tutte le sezioni.
 */
export const CONFERMA_ELIMINAZIONE_CLIENTE = {
  /** Il nome della riga lo conosce solo chi apre la finestra, quindi arriva da fuori. */
  TITOLO: (ragioneSociale: string): string => `Eliminare ${ragioneSociale}?`,
  /** L'effetto collaterale va detto prima: il backend cancella in cascata i siti. */
  MESSAGGIO: 'Verranno eliminati anche i siti collegati. Non si può annullare.',
  CONFERMA: 'Elimina',
} as const;
