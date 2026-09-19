import {
  ApplicationConfig,
  inject,
  LOCALE_ID,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeIt from '@angular/common/locales/it';
import { provideRouter } from '@angular/router';
import { MAT_DATE_LOCALE } from '@angular/material/core';

import { routes } from './app.routes';
import { AuthService } from './services/auth/service/auth.service';
import { sessioneScadutaInterceptor } from './services/auth/interceptors/sessione-scaduta.interceptor';

// I dati della lingua (nomi dei mesi, separatori, formati) non finiscono nel
// bundle da soli: senza questa riga `LOCALE_ID` punterebbe a un locale che
// Angular non ha, e le pipe fallirebbero a runtime.
registerLocaleData(localeIt);

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
    // La lingua dell'app, detta una volta per tutte. Sono due catene separate:
    // `LOCALE_ID` governa le pipe di Angular (date, numeri, valute),
    // `MAT_DATE_LOCALE` il calendario di Material. Prima stava solo sulla
    // seconda, ripetuta sui componenti che avevano un datepicker.
    { provide: LOCALE_ID, useValue: 'it-IT' },
    { provide: MAT_DATE_LOCALE, useValue: 'it-IT' },
  ],
};
