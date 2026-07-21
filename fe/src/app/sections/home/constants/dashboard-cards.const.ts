import { AppRoute } from '../../../shared/enums/app-route.enum';
import { DashboardCardInterface } from '../interfaces/dashboard-card.interface';
import {
  DashboardCardActionIcon,
  DashboardCardActionLabel,
  DashboardCardActionRoute,
  DashboardCardColor,
  DashboardCardDescription,
  DashboardCardIcon,
  DashboardCardTitle,
} from '../enums/dashboard-card.enum';

/** Azioni comuni alle sezioni di anagrafica: apri l'elenco oppure crea una nuova voce. */
const AZIONI_ANAGRAFICA = [
  {
    label: DashboardCardActionLabel.ELENCO,
    icona: DashboardCardActionIcon.ELENCO,
  },
  {
    label: DashboardCardActionLabel.NUOVO,
    icona: DashboardCardActionIcon.NUOVO,
    segmento: DashboardCardActionRoute.NUOVO,
  },
] as const;

export const DASHBOARD_CARDS: readonly DashboardCardInterface[] = [
  {
    titolo: DashboardCardTitle.CLIENTI,
    descrizione: DashboardCardDescription.CLIENTI,
    icona: DashboardCardIcon.CLIENTI,
    colore: DashboardCardColor.CLIENTI,
    route: AppRoute.CLIENTI,
    azioni: AZIONI_ANAGRAFICA,
  },
  {
    titolo: DashboardCardTitle.DIPENDENTI,
    descrizione: DashboardCardDescription.DIPENDENTI,
    icona: DashboardCardIcon.DIPENDENTI,
    colore: DashboardCardColor.DIPENDENTI,
    route: AppRoute.DIPENDENTI,
    azioni: AZIONI_ANAGRAFICA,
  },
  {
    titolo: DashboardCardTitle.SITI,
    descrizione: DashboardCardDescription.SITI,
    icona: DashboardCardIcon.SITI,
    colore: DashboardCardColor.SITI,
    route: AppRoute.SITI,
    azioni: AZIONI_ANAGRAFICA,
  },
  {
    titolo: DashboardCardTitle.TIMESHEET,
    descrizione: DashboardCardDescription.TIMESHEET,
    icona: DashboardCardIcon.TIMESHEET,
    colore: DashboardCardColor.TIMESHEET,
    route: AppRoute.TIMESHEET,
    azioni: [
      {
        label: DashboardCardActionLabel.INSERISCI_ORE,
        icona: DashboardCardActionIcon.INSERISCI_ORE,
      },
      {
        label: DashboardCardActionLabel.RIEPILOGO,
        icona: DashboardCardActionIcon.RIEPILOGO,
        segmento: DashboardCardActionRoute.RIEPILOGO,
      },
    ],
  },
];
