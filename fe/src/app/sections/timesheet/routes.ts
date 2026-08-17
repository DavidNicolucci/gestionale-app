import { Routes } from '@angular/router';

/** Rotte della sezione timesheet, montate sotto `AppRoute.TIMESHEET`: path relativi. */
export const timesheetRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./timesheet').then((m) => m.Timesheet),
  },
];
