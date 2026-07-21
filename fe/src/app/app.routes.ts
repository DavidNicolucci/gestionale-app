import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './services/auth/guards/auth.guard';
import { AppRoute } from './shared/enums/app-route.enum';

export const routes: Routes = [
  {
    path: AppRoute.HOME,
    pathMatch: 'full',
    canActivate: [authGuard],
    loadComponent: () => import('./sections/home/home').then((m) => m.Home),
  },
  {
    path: AppRoute.LOGIN,
    canActivate: [guestGuard],
    loadComponent: () => import('./sections/login/login').then((m) => m.Login),
  },
  { path: '**', redirectTo: AppRoute.HOME },
];
