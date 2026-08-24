import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatInput, MatLabel, MatSuffix } from '@angular/material/input';
import { RinnovoDialogDataInterface } from './interfaces/rinnovo-dialog-data.interface';
import { aDataIso } from '../../../../shared/utils/data.util';

/**
 * Chiede la nuova data di scadenza di un contratto.
 *
 * Non è una conferma con un campo in più: `ConfermaService` restituisce un booleano,
 * qui serve un dato. Chi la apre riceve la data in formato `yyyy-MM-dd`, oppure
 * `undefined` se ha annullato.
 */
@Component({
  selector: 'app-rinnovo-dialog',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButton,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatFormField,
    MatLabel,
    MatError,
    MatInput,
    MatSuffix,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
  ],
  templateUrl: './rinnovo-dialog.html',
  styleUrl: './rinnovo-dialog.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    // Come nel form di creazione: senza adapter il datepicker non sa leggere né
    // scrivere le date, e senza locale il calendario esce in inglese.
    provideNativeDateAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: 'it-IT' },
  ],
})
export class RinnovoDialog {
  protected readonly dati = inject<RinnovoDialogDataInterface>(MAT_DIALOG_DATA);

  /**
   * Sotto a oggi il datepicker non lascia scegliere: il backend rifiuterebbe con un
   * 409, e far sbagliare l'utente per poi dirgli di no è peggio che non farglielo
   * fare. Oggi è ammesso — la scadenza è inclusiva, l'ultimo giorno si lavora ancora.
   */
  protected readonly minimo = new Date();

  protected readonly dataScadenza = new FormControl<Date | null>(null, Validators.required);

  private readonly riferimento = inject(MatDialogRef<RinnovoDialog, string | undefined>);

  protected onRinnova(): void {
    if (this.dataScadenza.invalid) {
      // Senza questo il campo resta pulito e il messaggio d'errore non compare:
      // gli errori Material si vedono solo su un control "toccato".
      this.dataScadenza.markAsTouched();
      return;
    }

    this.riferimento.close(aDataIso(this.dataScadenza.value));
  }

  protected onAnnulla(): void {
    this.riferimento.close(undefined);
  }
}
