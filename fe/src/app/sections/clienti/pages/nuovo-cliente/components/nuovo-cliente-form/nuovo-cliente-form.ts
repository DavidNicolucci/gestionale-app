import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AbstractControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatButton } from '@angular/material/button';
import { NuovoClienteFormInterface } from '../../interfaces/nuovo-cliente-form.interface';
import { MESSAGGI_ERRORE } from '../../constants/messaggi-errore.constant';
import { LUNGHEZZA_MAX_PARTITA_IVA } from '../../../../constants/cliente.constants';

/**
 * Solo template e eventi: il form arriva già costruito dalla pagina, che è anche
 * l'unica a sapere cosa succede al salvataggio. Qui non si inietta nulla.
 */
@Component({
  selector: 'app-nuovo-cliente-form',
  imports: [ReactiveFormsModule, MatFormField, MatLabel, MatError, MatHint, MatInput, MatButton],
  templateUrl: './nuovo-cliente-form.html',
  styleUrl: './nuovo-cliente-form.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NuovoClienteForm {
  readonly form = input.required<FormGroup<NuovoClienteFormInterface>>();

  /** Salvataggio in corso: campi e pulsanti restano fermi finché non risponde. */
  readonly inSalvataggio = input(false);

  /** Deciso dalla pagina, che è l'unico punto che sa quando si può salvare. */
  readonly isSalvaDisabled = input(true);

  readonly salva = output<void>();
  readonly annulla = output<void>();

  protected readonly LUNGHEZZA_MAX_PARTITA_IVA = LUNGHEZZA_MAX_PARTITA_IVA;

  /**
   * Ogni chiave compare su un campo solo (`required` sulla ragione sociale,
   * `maxlength` sulla partita IVA), quindi il messaggio non è ambiguo.
   * `backend` viene prima: se il server ha bocciato il valore, è lui a spiegare perché.
   */
  protected messaggioErrore(control: AbstractControl): string {
    const errori = control.errors;

    if (!errori) {
      return '';
    }

    if (errori['backend']) {
      return errori['backend'] as string;
    }

    if (errori['required']) {
      return MESSAGGI_ERRORE.RAGIONE_SOCIALE_OBBLIGATORIA;
    }

    if (errori['maxlength']) {
      return MESSAGGI_ERRORE.PARTITA_IVA_TROPPO_LUNGA;
    }

    return '';
  }

  /**
   * `preventDefault()`: il `<form>` non ha direttive Angular, serve solo a far
   * salvare anche con Invio. L'invio vero lo fa la pagina.
   */
  protected onSubmit(event: Event): void {
    event.preventDefault();

    if (this.isSalvaDisabled()) {
      return;
    }

    this.salva.emit();
  }
}
