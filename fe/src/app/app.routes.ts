import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './services/auth/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [authGuard],
    loadComponent: () => import('./sections/home/home').then((m) => m.Home),
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./sections/login/login').then((m) => m.Login),
  },
  { path: '**', redirectTo: '' },
];
