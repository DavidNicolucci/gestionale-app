import { Routes } from '@angular/router';

/** Rotte della sezione dipendenti, montate sotto `AppRoute.DIPENDENTI`: path relativi. */
export const dipendentiRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./dipendenti').then((m) => m.Dipendenti),
  },
  {
    path: 'nuovo',
    loadComponent: () =>
      import('./pages/nuovo-dipendente/nuovo-dipendente').then((m) => m.NuovoDipendente),
  },
];
