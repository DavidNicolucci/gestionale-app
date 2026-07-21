import { AppRoute } from '../../../shared/enums/app-route.enum';
import { DashboardCardInterface } from '../interfaces/dashboard-card.interface';
import {
  DashboardCardDescription,
  DashboardCardIcon,
  DashboardCardTitle,
} from '../enums/dashboard-card.enum';

export const DASHBOARD_CARDS: readonly DashboardCardInterface[] = [
  {
    titolo: DashboardCardTitle.CLIENTI,
    descrizione: DashboardCardDescription.CLIENTI,
    icona: DashboardCardIcon.CLIENTI,
    route: AppRoute.CLIENTI,
  },
  {
    titolo: DashboardCardTitle.DIPENDENTI,
    descrizione: DashboardCardDescription.DIPENDENTI,
    icona: DashboardCardIcon.DIPENDENTI,
    route: AppRoute.DIPENDENTI,
  },
  {
    titolo: DashboardCardTitle.SITI,
    descrizione: DashboardCardDescription.SITI,
    icona: DashboardCardIcon.SITI,
    route: AppRoute.SITI,
  },
  {
    titolo: DashboardCardTitle.TIMESHEET,
    descrizione: DashboardCardDescription.TIMESHEET,
    icona: DashboardCardIcon.TIMESHEET,
    route: AppRoute.TIMESHEET,
  },
];
