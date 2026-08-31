import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AbstractControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatButton } from '@angular/material/button';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { NuovoSitoFormInterface } from '../../interfaces/nuovo-sito-form.interface';
import { MESSAGGI_ERRORE } from '../../constants/messaggi-errore.constant';
import { ClienteModel } from '../../../../../clienti/interfaces/cliente.model';

/**
 * Solo template e eventi: il form arriva già costruito dalla pagina, che è anche
 * l'unica a sapere cosa succede al salvataggio. Qui non si inietta nulla.
 */
@Component({
  selector: 'app-nuovo-sito-form',
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatLabel,
    MatError,
    MatHint,
    MatInput,
    MatButton,
    MatSelect,
    MatOption,
  ],
  templateUrl: './nuovo-sito-form.html',
  styleUrl: './nuovo-sito-form.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NuovoSitoForm {
  readonly form = input.required<FormGroup<NuovoSitoFormInterface>>();

  /** Clienti fra cui scegliere: li carica la pagina, qui si mostrano e basta. */
  readonly clienti = input<ClienteModel[]>([]);

  /**
   * Finché l'anagrafica non è arrivata la tendina resta ferma: aperta vuota
   * sembrerebbe che di clienti non ce ne siano.
   */
  readonly inCaricamentoClienti = input(false);

  /** Salvataggio in corso: campi e pulsanti restano fermi finché non risponde. */
  readonly inSalvataggio = input(false);

  /** Deciso dalla pagina, che è l'unico punto che sa quando si può salvare. */
  readonly isSalvaDisabled = input(true);

  readonly salva = output<void>();
  readonly annulla = output<void>();

  /**
   * `backend` viene prima: se il server ha bocciato il valore, è lui a spiegare
   * perché. Sotto resta solo `required`, che è l'unico vincolo dei due campi
   * obbligatori.
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
      return MESSAGGI_ERRORE.OBBLIGATORIO;
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
