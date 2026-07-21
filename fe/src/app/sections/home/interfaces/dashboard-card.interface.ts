import { AppRoute } from '../../../shared/enums/app-route.enum';
import {
  DashboardCardActionIcon,
  DashboardCardActionLabel,
  DashboardCardActionRoute,
  DashboardCardColor,
  DashboardCardDescription,
  DashboardCardIcon,
  DashboardCardTitle,
} from '../enums/dashboard-card.enum';

/** Azione rapida di una card: scorciatoia verso una pagina della sezione. */
export interface DashboardCardActionInterface {
  label: DashboardCardActionLabel;
  icona: DashboardCardActionIcon;
  /** Assente quando l'azione punta alla rotta della sezione stessa. */
  segmento?: DashboardCardActionRoute;
}

export interface DashboardCardInterface {
  titolo: DashboardCardTitle;
  descrizione: DashboardCardDescription;
  icona: DashboardCardIcon;
  colore: DashboardCardColor;
  route: AppRoute;
  azioni: readonly DashboardCardActionInterface[];
}
