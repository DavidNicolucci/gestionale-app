import { AppRoute } from '../../../shared/enums/app-route.enum';
import {
  DashboardCardDescription,
  DashboardCardIcon,
  DashboardCardTitle,
} from '../enums/dashboard-card.enum';

export interface DashboardCardInterface {
  titolo: DashboardCardTitle;
  descrizione: DashboardCardDescription;
  icona: DashboardCardIcon;
  route: AppRoute;
}
