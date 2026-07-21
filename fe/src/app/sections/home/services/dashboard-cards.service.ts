import { Injectable, signal } from '@angular/core';
import { DashboardCardInterface } from '../interfaces/dashboard-card.interface';
import { DASHBOARD_CARDS } from '../constants/dashboard-cards.const';

/** Espone le card della dashboard, tenendo i dati fuori dal componente. */
@Injectable({ providedIn: 'root' })
export class DashboardCardsService {
  private readonly _cards = signal<readonly DashboardCardInterface[]>(DASHBOARD_CARDS);
  readonly cards = this._cards.asReadonly();
}
