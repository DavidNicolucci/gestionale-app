import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AbstractControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatError, MatFormField, MatHint, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatButton } from '@angular/material/button';
import { MatSelect } from '@angular/material/select';
import { MatOption, provideNativeDateAdapter, MAT_DATE_LOCALE } from '@angular/material/core';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { DipendenteFormInterface } from '../../interfaces/dipendente-form.interface';
import { MESSAGGI_ERRORE } from '../../constants/messaggi-errore.constant';
import { LUNGHEZZA_CODICE_FISCALE } from '../../constants/dipendente.constants';
import {
  ETICHETTE_TIPO_CONTRATTO,
  TIPI_CONTRATTO,
  TipoContratto,
} from '../../enums/tipo-contratto.enum';

/**
 * Il modulo di un dipendente, usato sia in creazione sia in modifica: i campi e le
 * loro regole sono gli stessi, cambia solo cosa succede quando si preme il pulsante.
 *
 * Solo template e eventi: il form arriva già costruito dalla pagina, che è anche
 * l'unica a sapere cosa succede al salvataggio. Qui non si inietta nulla.
 */
@Component({
  selector: 'app-dipendente-form',
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatLabel,
    MatError,
    MatHint,
    MatSuffix,
    MatInput,
    MatButton,
    MatSelect,
    MatOption,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
  ],
  templateUrl: './dipendente-form.html',
  styleUrl: './dipendente-form.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    // Il datepicker non ha un adapter di default: senza questo non sa leggere né
    // scrivere le date. Locale italiano, altrimenti il calendario esce in inglese
    // e il campo si scrive nel formato americano.
    provideNativeDateAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: 'it-IT' },
  ],
})
export class DipendenteForm {
  readonly form = input.required<FormGroup<DipendenteFormInterface>>();

  /** Salvataggio in corso: campi e pulsanti restano fermi finché non risponde. */
  readonly inSalvataggio = input(false);

  /** Deciso dalla pagina, che è l'unico punto che sa quando si può salvare. */
  readonly isSalvaDisabled = input(true);

  /**
   * Deciso dalla pagina, che segue il tipo di contratto: un indeterminato non
   * scade, quindi il campo non compare invece di restare vuoto e ignorato.
   */
  readonly mostraScadenza = input(false);

  /**
   * Testo del pulsante di conferma. Lo decide la pagina: "Salva dipendente" in
   * creazione, "Salva modifiche" in modifica. Un solo modulo, due gesti diversi, e
   * chi lo usa deve leggere quale dei due sta facendo.
   */
  readonly etichettaSalva = input('Salva dipendente');

  readonly salva = output<void>();
  readonly annulla = output<void>();

  protected readonly LUNGHEZZA_CODICE_FISCALE = LUNGHEZZA_CODICE_FISCALE;
  protected readonly TIPI_CONTRATTO = TIPI_CONTRATTO;
  protected readonly ETICHETTE_TIPO_CONTRATTO = ETICHETTE_TIPO_CONTRATTO;

  /**
   * `@Past` sul backend: oggi non basta, deve essere un giorno già passato.
   * Limitando il calendario l'errore si evita invece di spiegarlo dopo il 400.
   */
  protected readonly maxDataNascita = DipendenteForm.ieri();

  private static ieri(): Date {
    const data = new Date();
    data.setDate(data.getDate() - 1);
    return data;
  }

  protected etichettaContratto(tipoContratto: TipoContratto): string {
    return ETICHETTE_TIPO_CONTRATTO[tipoContratto];
  }

  /**
   * `backend` viene prima: se il server ha bocciato il valore, è lui a spiegare
   * perché. Poi le chiavi in ordine di specificità, dalla più precisa alla più
   * generica: `required` è l'ultima perché è l'unica che compare su sette campi.
   */
  protected messaggioErrore(control: AbstractControl): string {
    const errori = control.errors;

    if (!errori) {
      return '';
    }

    if (errori['backend']) {
      return errori['backend'] as string;
    }

    if (errori['minlength'] || errori['maxlength']) {
      return MESSAGGI_ERRORE.CODICE_FISCALE_LUNGHEZZA;
    }

    // Il datepicker segnala così una data scritta a mano che non sta in piedi.
    if (errori['matDatepickerParse']) {
      return MESSAGGI_ERRORE.DATA_NON_VALIDA;
    }

    if (errori['matDatepickerMax']) {
      return MESSAGGI_ERRORE.DATA_NASCITA_NON_PASSATA;
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
