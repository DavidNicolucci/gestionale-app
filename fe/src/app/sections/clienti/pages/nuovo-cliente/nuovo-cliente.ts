import { ChangeDetectionStrategy, Component, inject, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { Router } from '@angular/router';
import { FormGroup } from '@angular/forms';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AppRoute } from '../../../../shared/enums/app-route.enum';
import { GoBack } from '../../../../shared/components/go-back/go-back';
import { NuovoClienteForm } from './components/nuovo-cliente-form/nuovo-cliente-form';
import { NuovoClienteFormInterface } from './interfaces/nuovo-cliente-form.interface';
import { NuovoClienteFormService } from './services/nuovo-cliente-form.service';
import { NuovoClienteQueryService } from './services/nuovo-cliente-query.service';

/**
 * Pagina di creazione cliente: non costruisce il form, non valida e non compone
 * il body. Mette insieme i pezzi e decide dove si va dopo il salvataggio.
 */
@Component({
  selector: 'app-nuovo-cliente',
  imports: [GoBack, NuovoClienteForm, MatProgressSpinner],
  templateUrl: './nuovo-cliente.html',
  styleUrl: './nuovo-cliente.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Forniti qui e non in root: form e stato del salvataggio vivono quanto la pagina.
  providers: [NuovoClienteFormService, NuovoClienteQueryService],
})
export class NuovoCliente {
  /** Quanto resta a schermo la conferma dopo il salvataggio. */
  private static readonly DURATA_CONFERMA_MS = 4000;
  protected readonly form: FormGroup<NuovoClienteFormInterface>;
  protected readonly isSalvaDisabled: Signal<boolean>;
  protected readonly query = inject(NuovoClienteQueryService);
  protected readonly AppRoute = AppRoute;
  private readonly formService = inject(NuovoClienteFormService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  constructor() {
    this.form = this.formService.createMainForm();

    // Da observable e non da `form.invalid` letto nel template: così il pulsante
    // si aggiorna da solo, anche quando a invalidare il form è il backend.
    // Parte disabilitato perché la ragione sociale è obbligatoria e nasce vuota.
    this.isSalvaDisabled = toSignal(this.form.statusChanges.pipe(map(() => this.form.invalid)), {
      initialValue: true,
    });
  }

  /** Si torna all'elenco solo se il salvataggio è andato a buon fine. */
  protected async onSalva(): Promise<void> {
    const cliente = await this.query.salvaCliente(this.form);

    if (!cliente) {
      return;
    }

    this.snackBar.open(`Cliente "${cliente.ragioneSociale}" creato`, 'Chiudi', {
      duration: NuovoCliente.DURATA_CONFERMA_MS,
    });

    void this.router.navigate([AppRoute.CLIENTI]);
  }

  protected onAnnulla(): void {
    void this.router.navigate([AppRoute.CLIENTI]);
  }
}
