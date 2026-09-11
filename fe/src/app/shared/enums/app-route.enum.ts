/**
 * Path assoluti dalla root, unica fonte di verità per la navigazione.
 * Nei `routes.ts` i path restano relativi: qui si compongono dal padre
 * con template literal, senza mai riscriverli per intero.
 */
export enum AppRoute {
  HOME = '',
  LOGIN = 'login',

  // ***** CLIENTI *****
  CLIENTI = 'clienti',
  NUOVO_CLIENTE = `${CLIENTI}/nuovo`,

  // ***** DIPENDENTI *****
  DIPENDENTI = 'dipendenti',
  NUOVO_DIPENDENTE = `${DIPENDENTI}/nuovo`,
  // Path senza l'id: la pagina si apre con navigate([MODIFICA_DIPENDENTE, id]),
  // cosi' l'enum resta un elenco di path fissi come tutti gli altri.
  MODIFICA_DIPENDENTE = `${DIPENDENTI}/modifica`,

  // ***** SITI *****
  SITI = 'siti',
  NUOVO_SITO = `${SITI}/nuovo`,

  // ***** ALTRE SEZIONI *****
  TIMESHEET = 'timesheet',
}
