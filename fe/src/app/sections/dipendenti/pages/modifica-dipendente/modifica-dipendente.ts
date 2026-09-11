import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  Signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map, merge } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { FormGroup } from '@angular/forms';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AppRoute } from '../../../../shared/enums/app-route.enum';
import { GoBack } from '../../../../shared/components/go-back/go-back';
import { DipendenteForm } from '../../components/dipendente-form/dipendente-form';
import { DipendenteFormInterface } from '../../interfaces/dipendente-form.interface';
import { DipendenteFormService } from '../../services/dipendente-form.service';
import { ModificaDipendenteQueryService } from './services/modifica-dipendente-query.service';
import { TipoContratto } from '../../enums/tipo-contratto.enum';

/**
 * Pagina di modifica dipendente. Stessa struttura della creazione — non costruisce
 * il form, non valida e non compone il body — con in più il caricamento iniziale:
 * qui i campi non nascono vuoti, arrivano da quello che è già salvato.
 */
@Component({
  selector: 'app-modifica-dipendente',
  imports: [GoBack, DipendenteForm, MatProgressSpinner],
  templateUrl: './modifica-dipendente.html',
  styleUrl: './modifica-dipendente.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Forniti qui e non in root: form e stato della pagina vivono quanto la pagina.
  providers: [DipendenteFormService, ModificaDipendenteQueryService],
})
export class ModificaDipendente {
  /** Quanto resta a schermo la conferma dopo il salvataggio. */
  private static readonly DURATA_CONFERMA_MS = 4000;

  protected readonly form: FormGroup<DipendenteFormInterface>;
  protected readonly isSalvaDisabled: Signal<boolean>;
  /** Un contratto a termine ha una scadenza, un indeterminato no. */
  protected readonly mostraScadenza: Signal<boolean>;
  protected readonly query = inject(ModificaDipendenteQueryService);
  protected readonly AppRoute = AppRoute;

  private readonly tipoContratto: Signal<TipoContratto | null>;
  private readonly formService = inject(DipendenteFormService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  /** Letto una volta sola: la pagina non si riusa per un altro id senza ricrearsi. */
  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  constructor() {
    this.form = this.formService.createMainForm();

    // `merge` dei due flussi e non il solo `statusChanges` come in creazione: qui il
    // pulsante dipende anche da `pristine`, che cambia al primo tocco su un form già
    // valido — e in quel caso `statusChanges` non emette, perché lo stato di validità
    // non è cambiato. Parte disabilitato: appena caricato non c'è niente da salvare.
    this.isSalvaDisabled = toSignal(
      merge(this.form.statusChanges, this.form.valueChanges).pipe(
        map(() => this.form.invalid || this.form.pristine),
      ),
      { initialValue: true },
    );

    this.tipoContratto = toSignal(this.form.controls.tipoContratto.valueChanges, {
      initialValue: null,
    });

    this.mostraScadenza = computed(() => this.tipoContratto() === TipoContratto.DETERMINATO);

    // La scadenza cambia regole quando cambia il contratto, e i validator di un
    // control non si rivalutano da soli quando ne cambia un altro. L'effect non
    // torna a scattare da sé: tocca `dataScadenza`, non `tipoContratto`.
    effect(() => this.formService.aggiornaScadenza(this.form, this.tipoContratto()));

    void this.query.carica(this.id, this.form);
  }

  /** Si torna all'elenco solo se il salvataggio è andato a buon fine. */
  protected async onSalva(): Promise<void> {
    const dipendente = await this.query.salva(this.id, this.form);

    if (!dipendente) {
      return;
    }

    this.snackBar.open(
      `Dipendente "${dipendente.cognome} ${dipendente.nome}" aggiornato`,
      'Chiudi',
      { duration: ModificaDipendente.DURATA_CONFERMA_MS },
    );

    void this.router.navigate([AppRoute.DIPENDENTI]);
  }

  protected onAnnulla(): void {
    void this.router.navigate([AppRoute.DIPENDENTI]);
  }
}
