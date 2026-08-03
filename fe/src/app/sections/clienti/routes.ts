import { Routes } from '@angular/router';

/** Rotte della sezione clienti, montate sotto `AppRoute.CLIENTI`. */
export const clientiRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./clienti').then((m) => m.Clienti),
  },
];
