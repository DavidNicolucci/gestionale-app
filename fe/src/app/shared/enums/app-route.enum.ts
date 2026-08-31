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

  // ***** SITI *****
  SITI = 'siti',
  NUOVO_SITO = `${SITI}/nuovo`,

  // ***** ALTRE SEZIONI *****
  TIMESHEET = 'timesheet',
}
