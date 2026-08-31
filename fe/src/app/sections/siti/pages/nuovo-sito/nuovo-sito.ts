import { ChangeDetectionStrategy, Component, inject, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { Router } from '@angular/router';
import { FormGroup } from '@angular/forms';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AppRoute } from '../../../../shared/enums/app-route.enum';
import { GoBack } from '../../../../shared/components/go-back/go-back';
import { NuovoSitoForm } from './components/nuovo-sito-form/nuovo-sito-form';
import { NuovoSitoFormInterface } from './interfaces/nuovo-sito-form.interface';
import { NuovoSitoFormService } from './services/nuovo-sito-form.service';
import { NuovoSitoQueryService } from './services/nuovo-sito-query.service';

/**
 * Pagina di creazione sito: non costruisce il form, non valida e non compone il
 * body. Mette insieme i pezzi e decide dove si va dopo il salvataggio.
 */
@Component({
  selector: 'app-nuovo-sito',
  imports: [GoBack, NuovoSitoForm, MatProgressSpinner],
  templateUrl: './nuovo-sito.html',
  styleUrl: './nuovo-sito.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Forniti qui e non in root: form e stato del salvataggio vivono quanto la pagina.
  providers: [NuovoSitoFormService, NuovoSitoQueryService],
})
export class NuovoSito {
  /** Quanto resta a schermo la conferma dopo il salvataggio. */
  private static readonly DURATA_CONFERMA_MS = 4000;
  protected readonly form: FormGroup<NuovoSitoFormInterface>;
  protected readonly isSalvaDisabled: Signal<boolean>;
  protected readonly query = inject(NuovoSitoQueryService);
  protected readonly AppRoute = AppRoute;
  private readonly formService = inject(NuovoSitoFormService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  constructor() {
    this.form = this.formService.createMainForm();

    // Da observable e non da `form.invalid` letto nel template: così il pulsante
    // si aggiorna da solo, anche quando a invalidare il form è il backend.
    // Parte disabilitato perché i campi obbligatori nascono vuoti.
    this.isSalvaDisabled = toSignal(this.form.statusChanges.pipe(map(() => this.form.invalid)), {
      initialValue: true,
    });
  }

  /** Si torna all'elenco solo se il salvataggio è andato a buon fine. */
  protected async onSalva(): Promise<void> {
    const sito = await this.query.salvaSito(this.form);

    if (!sito) {
      return;
    }

    this.snackBar.open(`Sito "${sito.nome}" creato`, 'Chiudi', {
      duration: NuovoSito.DURATA_CONFERMA_MS,
    });

    void this.router.navigate([AppRoute.SITI]);
  }

  protected onAnnulla(): void {
    void this.router.navigate([AppRoute.SITI]);
  }
}
