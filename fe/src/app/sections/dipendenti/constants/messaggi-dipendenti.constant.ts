/**
 * Testi delle conferme della sezione dipendenti. Stanno qui e non nel componente
 * perché non sono logica di pagina ma contenuto.
 *
 * Nota: la cancellazione è logica, non fisica. La riga resta sul database e con lei
 * i timesheet che la referenziano — le ore già consuntivate non spariscono con la
 * persona. Per questo un dipendente si può eliminare anche se ha dei timesheet, al
 * contrario di com'era prima, e può essere ripristinato.
 */
export const CONFERMA_ELIMINAZIONE_DIPENDENTE = {
  /** Il nome della riga lo conosce solo chi apre la finestra, quindi arriva da fuori. */
  TITOLO: (nominativo: string): string => `Eliminare ${nominativo}?`,
  MESSAGGIO:
    'Il dipendente sparirà dagli elenchi e non potrà più essere usato, ma le ore già registrate restano. Potrai ripristinarlo attivando il filtro "Mostra eliminati".',
  CONFERMA: 'Elimina',
} as const;

export const CONFERMA_RIPRISTINO_DIPENDENTE = {
  TITOLO: (nominativo: string): string => `Ripristinare ${nominativo}?`,
  /** Il ripristino non tocca il contratto: se era anche scaduto, resta da rinnovare. */
  MESSAGGIO:
    'Il dipendente tornerà negli elenchi. Se nel frattempo il contratto è scaduto, dovrai anche rinnovarlo prima di potergli registrare delle ore.',
  CONFERMA: 'Ripristina',
} as const;
