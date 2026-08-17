import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AppRoute } from '../../shared/enums/app-route.enum';
import { GoBack } from '../../shared/components/go-back/go-back';
import { TimesheetUpload } from './components/timesheet-upload/timesheet-upload';
import { TimesheetImportQueryService } from './services/timesheet-import-query.service';

/**
 * Pagina timesheet: da qui si carica il file Excel delle ore. Non valida il file
 * e non parla col backend, mette insieme i pezzi e conferma a fine caricamento.
 */
@Component({
  selector: 'app-timesheet',
  imports: [GoBack, TimesheetUpload, MatProgressSpinner],
  templateUrl: './timesheet.html',
  styleUrl: './timesheet.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Fornito qui e non in root: file scelto ed errore vivono quanto la pagina.
  providers: [TimesheetImportQueryService],
})
export class Timesheet {
  /** Quanto resta a schermo la conferma dopo il caricamento. */
  private static readonly DURATA_CONFERMA_MS = 6000;

  protected readonly query = inject(TimesheetImportQueryService);

  /** Esposto al template per il pulsante "Indietro". */
  protected readonly AppRoute = AppRoute;

  private readonly snackBar = inject(MatSnackBar);

  protected onFile(file: File | null): void {
    this.query.selezionaFile(file);
  }

  /**
   * La conferma dice "in elaborazione" e non "importato": il backend risponde
   * appena ha ricevuto il file, le righe vengono lavorate dopo in coda.
   */
  protected async onImporta(): Promise<void> {
    const messaggio = await this.query.importa();

    if (!messaggio) {
      return;
    }

    this.snackBar.open(messaggio, 'Chiudi', { duration: Timesheet.DURATA_CONFERMA_MS });
  }
}
