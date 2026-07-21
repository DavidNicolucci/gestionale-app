import { AppRoute } from '../../../shared/enums/app-route.enum';
import { DashboardCardInterface } from '../interfaces/dashboard-card.interface';
import {
  DashboardCardColor,
  DashboardCardDescription,
  DashboardCardIcon,
  DashboardCardTitle,
} from '../enums/dashboard-card.enum';

export const DASHBOARD_CARDS: readonly DashboardCardInterface[] = [
  {
    titolo: DashboardCardTitle.CLIENTI,
    descrizione: DashboardCardDescription.CLIENTI,
    icona: DashboardCardIcon.CLIENTI,
    colore: DashboardCardColor.CLIENTI,
    route: AppRoute.CLIENTI,
  },
  {
    titolo: DashboardCardTitle.DIPENDENTI,
    descrizione: DashboardCardDescription.DIPENDENTI,
    icona: DashboardCardIcon.DIPENDENTI,
    colore: DashboardCardColor.DIPENDENTI,
    route: AppRoute.DIPENDENTI,
  },
  {
    titolo: DashboardCardTitle.SITI,
    descrizione: DashboardCardDescription.SITI,
    icona: DashboardCardIcon.SITI,
    colore: DashboardCardColor.SITI,
    route: AppRoute.SITI,
  },
  {
    titolo: DashboardCardTitle.TIMESHEET,
    descrizione: DashboardCardDescription.TIMESHEET,
    icona: DashboardCardIcon.TIMESHEET,
    colore: DashboardCardColor.TIMESHEET,
    route: AppRoute.TIMESHEET,
  },
];
