/**
 * Testi delle conferme della sezione siti. Stanno qui e non nel componente perché
 * non sono logica di pagina ma contenuto.
 *
 * Nota: qui la cancellazione è fisica, al contrario di quella dei dipendenti. La
 * riga sparisce dal database e non c'è un ripristino, quindi la conferma lo dice
 * chiaramente invece di promettere un ripensamento che non esiste.
 */
export const CONFERMA_ELIMINAZIONE_SITO = {
  /** Il nome della riga lo conosce solo chi apre la finestra, quindi arriva da fuori. */
  TITOLO: (nome: string): string => `Eliminare ${nome}?`,
  MESSAGGIO:
    'Il sito verrà rimosso definitivamente e non potrà essere ripristinato. Se ci sono ore registrate su questo sito, l’eliminazione non andrà a buon fine.',
  CONFERMA: 'Elimina',
} as const;
