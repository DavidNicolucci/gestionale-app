import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { AuthService } from '../../services/auth/service/auth.service';
import { DashboardCard } from './components/dashboard-card/dashboard-card';
import { DashboardCardsService } from './services/dashboard-cards.service';
import { AppRoute } from '../../shared/enums/app-route.enum';

@Component({
  selector: 'app-home',
  imports: [MatButton, DashboardCard],
  templateUrl: './home.html',
  styleUrl: './home.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Home {
  protected readonly loggingOut = signal(false);
  protected readonly cards = inject(DashboardCardsService).cards;
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  getUsername(): string | null {
    return this.auth.getUsername();
  }

  async onLogout(): Promise<void> {
    if (this.loggingOut()) {
      return;
    }

    this.loggingOut.set(true);
    try {
      await this.auth.logout();
    } finally {
      this.loggingOut.set(false);
      await this.router.navigate(['/', AppRoute.LOGIN]);
    }
  }
}
