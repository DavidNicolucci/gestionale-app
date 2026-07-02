import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {firstValueFrom, Observable, tap} from 'rxjs';
import {plainToInstance} from 'class-transformer';
import {LoginRequestModel} from '../interfaces/login-request.model';
import {LoginResponseModel} from '../interfaces/login-response.model';





@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private static readonly TOKEN_KEY = 'gestionale_token';

  private readonly token = signal<string | null>(
    localStorage.getItem(AuthService.TOKEN_KEY),
  );

  readonly isAuthenticated = computed(() => this.token() !== null);

  getToken(): string | null {
    return this.token();
  }

  async login(body: LoginRequestModel): Promise<LoginResponseModel> {
    const post$ = this.http.post('/api/auth/login', body);
    const response = await firstValueFrom(post$);
    const result = plainToInstance(LoginResponseModel, response);
    this.setToken(result.token);
    return result;
  }

  logout(): void {
    localStorage.removeItem(AuthService.TOKEN_KEY);
    this.token.set(null);
  }

  private setToken(token: string): void {
    localStorage.setItem(AuthService.TOKEN_KEY, token);
    this.token.set(token);
  }
}
