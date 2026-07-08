import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth/service/auth.service';
import { LoginFormInterface } from './interfaces/login-form.interface';
import {MatCard, MatCardContent, MatCardHeader, MatCardTitle} from '@angular/material/card';
import {MatFormField, MatInput, MatLabel} from '@angular/material/input';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {MatButton} from '@angular/material/button';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatCard, MatCardHeader, MatCardTitle, MatCardContent, MatFormField, MatLabel, MatInput, MatButton, MatProgressSpinner],
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  protected readonly form = this.initForm();
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);


  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.loading()) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    try {
      await this.auth.login(this.form.getRawValue());
      await this.router.navigate(['/']);
    } catch {
      this.errorMessage.set('Credenziali non valide');
    } finally {
      this.loading.set(false);
    }
  }

  private initForm(): FormGroup<LoginFormInterface> {
    return new FormGroup<LoginFormInterface>({
      username: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required],
      }),
      password: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required],
      }),
    });
  }
}
