import { Routes } from '@angular/router';

/** Rotte della sezione clienti, montate sotto `AppRoute.CLIENTI`: path relativi. */
export const clientiRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./clienti').then((m) => m.Clienti),
  },
  {
    path: 'nuovo',
    loadComponent: () => import('./pages/nuovo-cliente/nuovo-cliente').then((m) => m.NuovoCliente),
  },
];
