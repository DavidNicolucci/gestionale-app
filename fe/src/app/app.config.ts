import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { AuthService } from './services/auth/service/auth.service';
import { sessioneScadutaInterceptor } from './services/auth/interceptors/sessione-scaduta.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Il 401 lo gestisce l'interceptor per tutti: è l'unico modo per accorgersi
    // che il cookie di sessione è scaduto. Gli altri stati restano ai singoli
    // service, che sanno cosa stavano chiedendo e sanno cosa dire.
    provideHttpClient(withInterceptors([sessioneScadutaInterceptor])),
    // All'avvio chiede al backend se il cookie di sessione è ancora valido:
    // così il login sopravvive al refresh della pagina
    provideAppInitializer(() => inject(AuthService).loadSession()),
  ],
};
