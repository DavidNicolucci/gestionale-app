import { AppRoute } from '../../../shared/enums/app-route.enum';
import {
  DashboardCardColor,
  DashboardCardDescription,
  DashboardCardIcon,
  DashboardCardTitle,
} from '../enums/dashboard-card.enum';

export interface DashboardCardInterface {
  titolo: DashboardCardTitle;
  descrizione: DashboardCardDescription;
  icona: DashboardCardIcon;
  colore: DashboardCardColor;
  route: AppRoute;
}
