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
  {
    // L'id sta nel path e non fra i query param: e' quale dipendente si sta
    // modificando, non un'opzione della pagina. Va dopo 'nuovo', che essendo un
    // segmento scritto per esteso non rischia di essere scambiato per un id.
    path: 'modifica/:id',
    loadComponent: () =>
      import('./pages/modifica-dipendente/modifica-dipendente').then((m) => m.ModificaDipendente),
  },
];
