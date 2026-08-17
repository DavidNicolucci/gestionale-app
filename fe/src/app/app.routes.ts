import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './services/auth/guards/auth.guard';
import { AppRoute } from './shared/enums/app-route.enum';

export const routes: Routes = [
  // Prima del guscio: il login è l'unica pagina che sta fuori dall'area
  // autenticata, e lì l'assistente non deve comparire.
  {
    path: AppRoute.LOGIN,
    canActivate: [guestGuard],
    loadComponent: () => import('./sections/login/login').then((m) => m.Login),
  },
  {
    // Guscio delle pagine autenticate: il guard sta qui una volta sola, invece
    // che ripetuto su ogni sezione, e vale anche per quelle che verranno.
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./components/layout-autenticato/layout-autenticato').then((m) => m.LayoutAutenticato),
    children: [
      {
        path: AppRoute.HOME,
        pathMatch: 'full',
        loadComponent: () => import('./sections/home/home').then((m) => m.Home),
      },
      {
        path: AppRoute.CLIENTI,
        loadChildren: () => import('./sections/clienti/routes').then((m) => m.clientiRoutes),
      },
      {
        path: AppRoute.TIMESHEET,
        loadChildren: () => import('./sections/timesheet/routes').then((m) => m.timesheetRoutes),
      },
    ],
  },
  { path: '**', redirectTo: AppRoute.HOME },
];
