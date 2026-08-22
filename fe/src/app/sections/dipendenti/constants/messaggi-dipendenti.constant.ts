/**
 * Testi delle conferme della sezione dipendenti. Stanno qui e non nel componente
 * perché non sono logica di pagina ma contenuto.
 *
 * Nota: a differenza dei clienti, qui NON c'è cancellazione a cascata. La FK
 * `fk_ts_dipendente` non ha `ON DELETE CASCADE`, quindi un dipendente con dei
 * timesheet non si può eliminare e il backend risponde 409.
 */
export const CONFERMA_ELIMINAZIONE_DIPENDENTE = {
  /** Il nome della riga lo conosce solo chi apre la finestra, quindi arriva da fuori. */
  TITOLO: (nominativo: string): string => `Eliminare ${nominativo}?`,
  MESSAGGIO:
    'Il dipendente verrà rimosso definitivamente. Se ha già dei timesheet registrati non sarà possibile eliminarlo.',
  CONFERMA: 'Elimina',
} as const;
