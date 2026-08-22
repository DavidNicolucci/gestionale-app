import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  Signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { Router } from '@angular/router';
import { FormGroup } from '@angular/forms';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AppRoute } from '../../../../shared/enums/app-route.enum';
import { GoBack } from '../../../../shared/components/go-back/go-back';
import { NuovoDipendenteForm } from './components/nuovo-dipendente-form/nuovo-dipendente-form';
import { NuovoDipendenteFormInterface } from './interfaces/nuovo-dipendente-form.interface';
import { NuovoDipendenteFormService } from './services/nuovo-dipendente-form.service';
import { NuovoDipendenteQueryService } from './services/nuovo-dipendente-query.service';
import { TipoContratto } from '../../enums/tipo-contratto.enum';

/**
 * Pagina di creazione dipendente: non costruisce il form, non valida e non
 * compone il body. Mette insieme i pezzi e decide dove si va dopo il salvataggio.
 */
@Component({
  selector: 'app-nuovo-dipendente',
  imports: [GoBack, NuovoDipendenteForm, MatProgressSpinner],
  templateUrl: './nuovo-dipendente.html',
  styleUrl: './nuovo-dipendente.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Forniti qui e non in root: form e stato del salvataggio vivono quanto la pagina.
  providers: [NuovoDipendenteFormService, NuovoDipendenteQueryService],
})
export class NuovoDipendente {
  /** Quanto resta a schermo la conferma dopo il salvataggio. */
  private static readonly DURATA_CONFERMA_MS = 4000;
  protected readonly form: FormGroup<NuovoDipendenteFormInterface>;
  protected readonly isSalvaDisabled: Signal<boolean>;
  /** Un contratto a termine ha una scadenza, un indeterminato no. */
  protected readonly mostraScadenza: Signal<boolean>;
  protected readonly query = inject(NuovoDipendenteQueryService);
  protected readonly AppRoute = AppRoute;
  private readonly tipoContratto: Signal<TipoContratto | null>;
  private readonly formService = inject(NuovoDipendenteFormService);
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

    this.tipoContratto = toSignal(this.form.controls.tipoContratto.valueChanges, {
      initialValue: null,
    });

    this.mostraScadenza = computed(() => this.tipoContratto() === TipoContratto.DETERMINATO);

    // La scadenza cambia regole quando cambia il contratto, e i validator di un
    // control non si rivalutano da soli quando ne cambia un altro. L'effect non
    // torna a scattare da sé: tocca `dataScadenza`, non `tipoContratto`.
    effect(() => this.formService.aggiornaScadenza(this.form, this.tipoContratto()));
  }

  /** Si torna all'elenco solo se il salvataggio è andato a buon fine. */
  protected async onSalva(): Promise<void> {
    const dipendente = await this.query.salvaDipendente(this.form);

    if (!dipendente) {
      return;
    }

    this.snackBar.open(`Dipendente "${dipendente.cognome} ${dipendente.nome}" creato`, 'Chiudi', {
      duration: NuovoDipendente.DURATA_CONFERMA_MS,
    });

    void this.router.navigate([AppRoute.DIPENDENTI]);
  }

  protected onAnnulla(): void {
    void this.router.navigate([AppRoute.DIPENDENTI]);
  }
}
