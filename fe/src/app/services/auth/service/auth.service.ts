import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { LoginRequestModel } from '../interfaces/login-request.model';
import { LoginResponseModel } from '../interfaces/login-response.model';
import { Ruolo } from '../../../shared/enums/ruolo.enum';

@Injectable({ providedIn: 'root' })
export class AuthService {
  /** Chi può parlare con l'assistente: gli stessi ruoli del `@RolesAllowed` su `/api/chat`. */
  private static readonly RUOLI_ASSISTENTE: readonly string[] = [Ruolo.ADMIN, Ruolo.OPERATOR];

  private readonly http = inject(HttpClient);

  // Il JWT vive in un cookie HttpOnly gestito dal browser e non è leggibile da qui:
  // teniamo in memoria solo chi è l'utente loggato
  private readonly currentUser = signal<string | null>(null);

  /** Ruoli dell'utente: il backend li manda insieme allo username, non stanno nel cookie leggibile. */
  private readonly ruoli = signal<string[]>([]);

  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  /**
   * Se mostrare l'assistente AI. Non è un controllo di sicurezza — chi non ha il
   * ruolo prenderebbe comunque un 403 — serve a non offrire un pulsante che non
   * funziona.
   */
  readonly puoUsareAssistente = computed(() =>
    this.ruoli().some((ruolo) => AuthService.RUOLI_ASSISTENTE.includes(ruolo)),
  );

  getUsername(): string | null {
    return this.currentUser();
  }

  async login(body: LoginRequestModel): Promise<LoginResponseModel> {
    const response = await firstValueFrom(this.http.post('/api/auth/login', body));
    const result = plainToInstance(LoginResponseModel, response);
    this.applicaSessione(result);
    return result;
  }

  async logout(): Promise<void> {
    // Il cookie è HttpOnly: solo il backend può cancellarlo. La stessa chiamata
    // cancella anche la conversazione con l'assistente, storico e memoria del modello.
    await firstValueFrom(this.http.post('/api/auth/logout', null));
    this.pulisciSessione();
  }

  /**
   * Chiamata all'avvio dell'app (vedi app.config.ts): se il cookie è ancora
   * valido recupera la sessione, così il login sopravvive al refresh della pagina.
   */
  async loadSession(): Promise<void> {
    try {
      const response = await firstValueFrom(this.http.get('/api/auth/me'));
      this.applicaSessione(plainToInstance(LoginResponseModel, response));
    } catch {
      this.pulisciSessione();
    }
  }

  private applicaSessione(sessione: LoginResponseModel): void {
    this.currentUser.set(sessione.username);
    // `?? []` perché la risposta arriva dal backend: se un giorno il campo non
    // ci fosse, meglio nessun ruolo che un errore a ogni lettura.
    this.ruoli.set(sessione.ruoli ?? []);
  }

  private pulisciSessione(): void {
    this.currentUser.set(null);
    this.ruoli.set([]);
  }
}
