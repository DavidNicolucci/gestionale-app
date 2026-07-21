import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../service/auth.service';
import { AppRoute } from '../../../shared/enums/app-route.enum';

/** Blocca le pagine protette se non c'è una sessione attiva. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? true : router.createUrlTree(['/', AppRoute.LOGIN]);
};

/** Tiene fuori dal login chi è già autenticato. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? router.createUrlTree(['/', AppRoute.HOME]) : true;
};
