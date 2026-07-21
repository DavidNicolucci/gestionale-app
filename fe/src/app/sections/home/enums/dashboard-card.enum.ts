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

/** Colore d'accento della card, iniettato nella custom property `--card-accent`. */
export enum DashboardCardColor {
  CLIENTI = '#6366f1',
  DIPENDENTI = '#10b981',
  SITI = '#f59e0b',
  TIMESHEET = '#0ea5e9',
}
