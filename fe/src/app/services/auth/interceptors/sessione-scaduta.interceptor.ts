import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../service/auth.service';
import { AppRoute } from '../../../shared/enums/app-route.enum';

/**
 * Chiamate dove il 401 è una risposta prevista e non una sessione caduta:
 * sul login vuol dire credenziali sbagliate e lo mostra il form, su `me` vuol
 * dire che all'avvio non c'era nessun cookie valido e se ne occupa
 * `AuthService.loadSession()`.
 */
const ROTTE_ESCLUSE: readonly string[] = ['/api/auth/login', '/api/auth/me'];

/**
 * Manda al login quando il cookie di sessione è scaduto.
 *
 * Il JWT sta in un cookie HttpOnly con una sua scadenza, che il frontend non
 * può leggere: finché non parte una chiamata, qui dentro la sessione sembra
 * ancora viva e il guard continua a far passare. Il primo 401 è l'unico
 * momento in cui lo veniamo a sapere.
 *
 * Solo il 401. Il 403 è una risposta di dominio — sei autenticato ma quella
 * cosa non la puoi fare — e va mostrata nella pagina in cui sei, non
 * trasformata in una cacciata al login.
 */
export const sessioneScadutaInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((errore: unknown) => {
      if (vaAlLogin(errore, req.url, auth)) {
        // Prima di navigare: `guestGuard` rimanda alla home chi risulta ancora
        // autenticato, e senza questo il login non si aprirebbe.
        const tornaA = router.url;
        auth.invalidaSessione();
        void router.navigate(['/', AppRoute.LOGIN], { queryParams: { returnUrl: tornaA } });
      }

      // L'errore prosegue comunque: la pagina che ha fatto la chiamata deve
      // poter spegnere il suo spinner.
      return throwError(() => errore);
    }),
  );
};

/**
 * Il controllo su `isAuthenticated()` fa anche da guardia contro le navigazioni
 * doppie: una pagina piena di tabelle manda più chiamate insieme e scadono
 * tutte allo stesso momento, ma la sessione la si invalida una volta sola.
 */
function vaAlLogin(errore: unknown, url: string, auth: AuthService): boolean {
  return (
    errore instanceof HttpErrorResponse &&
    errore.status === 401 &&
    auth.isAuthenticated() &&
    !ROTTE_ESCLUSE.some((rotta) => url.startsWith(rotta))
  );
}
