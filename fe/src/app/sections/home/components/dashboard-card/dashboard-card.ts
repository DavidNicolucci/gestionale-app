import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {DashboardCardActionInterface, DashboardCardInterface} from '../../interfaces/dashboard-card.interface';
import {MatIcon} from '@angular/material/icon';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-dashboard-card',
  imports: [
    MatIcon,
    RouterLink
  ],
  standalone: true,
  templateUrl: './dashboard-card.html',
  styleUrl: './dashboard-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardCard {
  readonly card = input.required<DashboardCardInterface>();

  /** Rotta della sezione, con l'eventuale segmento figlio dell'azione. */
  protected getAzioneLink(azione: DashboardCardActionInterface): string[] {
    const base = ['/', this.card().route];
    return azione.segmento ? [...base, azione.segmento] : base;
  }
}
