export enum DashboardCardTitle {
  CLIENTI = 'Clienti',
  DIPENDENTI = 'Dipendenti',
  SITI = 'Siti',
  TIMESHEET = 'Timesheet',
}

export enum DashboardCardDescription {
  CLIENTI = 'Anagrafica e gestione clienti',
  DIPENDENTI = 'Anagrafica dipendenti',
  SITI = 'Sedi e siti operativi',
  TIMESHEET = 'Consuntivazione ore',
}

/** Nomi delle icone Material usate dalle card. */
export enum DashboardCardIcon {
  CLIENTI = 'business',
  DIPENDENTI = 'badge',
  SITI = 'location_on',
  TIMESHEET = 'schedule',
}

/**
 * Colore d'accento della card, iniettato nella custom property `--card-accent`.
 * Rimanda alle variabili di `styles.scss`: lo stesso accento serve anche alle pagine
 * della sezione, e un esadecimale ripetuto in due posti prima o poi si scolla.
 */
export enum DashboardCardColor {
  CLIENTI = 'var(--accento-clienti)',
  DIPENDENTI = 'var(--accento-dipendenti)',
  SITI = 'var(--accento-siti)',
  TIMESHEET = 'var(--accento-timesheet)',
}

/** Etichette delle azioni rapide mostrate in fondo alla card. */
export enum DashboardCardActionLabel {
  ELENCO = 'Elenco',
  NUOVO = 'Nuovo',
  INSERISCI_ORE = 'Inserisci ore',
  RIEPILOGO = 'Riepilogo',
}

/** Icone Material delle azioni rapide. */
export enum DashboardCardActionIcon {
  ELENCO = 'list',
  NUOVO = 'add',
  INSERISCI_ORE = 'edit_calendar',
  RIEPILOGO = 'summarize',
}

/**
 * Segmento di rotta figlio, appeso alla rotta della sezione.
 * L'azione che punta alla sezione stessa (l'elenco) non ha segmento.
 */
export enum DashboardCardActionRoute {
  NUOVO = 'nuovo',
  RIEPILOGO = 'riepilogo',
}
