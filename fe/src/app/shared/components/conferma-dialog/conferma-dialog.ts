import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { ConfermaDialogDataInterface } from './interfaces/conferma-dialog-data.interface';

/** Etichette usate quando chi apre la finestra non ne passa di proprie. */
const ETICHETTE_DEFAULT = {
  CONFERMA: 'Conferma',
  ANNULLA: 'Annulla',
} as const;

/**
 * Finestra di conferma generica: la usa qualunque sezione prima di un'azione che
 * non si può annullare. Non sa cosa sta confermando e non ha colori suoi (niente
 * accento di sezione, niente rosso): riceve dei testi e restituisce sì o no.
 *
 * Non si apre a mano: ci pensa `ConfermaService`, che ne nasconde l'esistenza a
 * chi chiama e consegna direttamente il booleano.
 */
@Component({
  selector: 'app-conferma-dialog',
  imports: [MatButton, MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose],
  templateUrl: './conferma-dialog.html',
  styleUrl: './conferma-dialog.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfermaDialog {
  protected readonly dati = inject<ConfermaDialogDataInterface>(MAT_DIALOG_DATA);

  protected readonly etichettaConferma = this.dati.conferma ?? ETICHETTE_DEFAULT.CONFERMA;
  protected readonly etichettaAnnulla = this.dati.annulla ?? ETICHETTE_DEFAULT.ANNULLA;
}
