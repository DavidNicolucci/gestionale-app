import { Routes } from '@angular/router';

/** Rotte della sezione siti, montate sotto `AppRoute.SITI`: path relativi. */
export const sitiRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./siti').then((m) => m.Siti),
  },
  {
    path: 'nuovo',
    loadComponent: () => import('./pages/nuovo-sito/nuovo-sito').then((m) => m.NuovoSito),
  },
];
