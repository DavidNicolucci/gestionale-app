import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth/service/auth.service';
import { LoginFormInterface } from './interfaces/login-form.interface';
import { AppRoute } from '../../shared/enums/app-route.enum';
import { ErrorResponseModel } from '../../shared/interfaces/error-response.model';
import {MatFormField, MatInput, MatLabel, MatSuffix} from '@angular/material/input';
import {MatButton, MatIconButton} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';
import {MatProgressSpinner} from '@angular/material/progress-spinner';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatLabel,
    MatInput,
    MatSuffix,
    MatButton,
    MatIconButton,
    MatIcon,
    MatProgressSpinner,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  protected readonly form = this.initForm();
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly hidePassword = signal(true);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);


  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.loading()) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    try {
      await this.auth.login(this.form.getRawValue());
      await this.router.navigateByUrl(this.destinazione());
    } catch (errore) {
      this.errorMessage.set(this.messaggioErrore(errore));
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Dove andare dopo il login. `sessioneScadutaInterceptor` mette in coda la
   * pagina da cui l'utente è stato buttato fuori, così dopo aver rifatto
   * l'accesso ci torna invece di ripartire dalla home.
   *
   * Il valore arriva dalla barra degli indirizzi, quindi può averlo scritto
   * chiunque: passa solo se è un path interno. `//` va escluso a parte, è un
   * indirizzo verso un altro sito che sembra un path.
   */
  private destinazione(): string {
    const richiesta = this.route.snapshot.queryParamMap.get('returnUrl');

    if (richiesta?.startsWith('/') && !richiesta.startsWith('//')) {
      return richiesta;
    }

    return `/${AppRoute.HOME}`;
  }

  /**
   * Col 429 il backend ha smesso di controllare la password per un po': dire
   * "credenziali non valide" farebbe riprovare l'utente all'infinito con quella
   * giusta. Il testo, con i minuti da aspettare, lo prepara il backend.
   */
  private messaggioErrore(errore: unknown): string {
    if (errore instanceof HttpErrorResponse && errore.status === 429) {
      return (
        (errore.error as ErrorResponseModel | null)?.message ??
        'Troppi tentativi di accesso. Riprova fra qualche minuto.'
      );
    }
    return 'Credenziali non valide';
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
