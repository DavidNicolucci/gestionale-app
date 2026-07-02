import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { LoginRequestModel } from '../interfaces/login-request.model';
import { LoginResponseModel } from '../interfaces/login-response.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  // Il JWT vive in un cookie HttpOnly gestito dal browser e non è leggibile da qui:
  // teniamo in memoria solo chi è l'utente loggato
  private readonly currentUser = signal<string | null>(null);

  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  getUsername(): string | null {
    return this.currentUser();
  }

  async login(body: LoginRequestModel): Promise<LoginResponseModel> {
    const response = await firstValueFrom(this.http.post('/api/auth/login', body));
    const result = plainToInstance(LoginResponseModel, response);
    this.currentUser.set(result.username);
    return result;
  }

  async logout(): Promise<void> {
    // Il cookie è HttpOnly: solo il backend può cancellarlo
    await firstValueFrom(this.http.post('/api/auth/logout', null));
    this.currentUser.set(null);
  }

  /**
   * Chiamata all'avvio dell'app (vedi app.config.ts): se il cookie è ancora
   * valido recupera la sessione, così il login sopravvive al refresh della pagina.
   */
  async loadSession(): Promise<void> {
    try {
      const response = await firstValueFrom(this.http.get('/api/auth/me'));
      const result = plainToInstance(LoginResponseModel, response);
      this.currentUser.set(result.username);
    } catch {
      this.currentUser.set(null);
    }
  }
}
