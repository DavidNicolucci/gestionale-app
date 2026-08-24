import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';
import { RinnovoDialog } from '../components/rinnovo-dialog/rinnovo-dialog';
import { RinnovoDialogDataInterface } from '../components/rinnovo-dialog/interfaces/rinnovo-dialog-data.interface';
import { DipendenteModel } from '../interfaces/dipendente.model';

/** Larghezza fissa: dentro c'è un campo solo, più larga sembrerebbe vuota. */
const LARGHEZZA = '26rem';

/**
 * Chiede la nuova data di scadenza e restituisce `yyyy-MM-dd`, oppure `undefined`
 * se l'utente ha annullato. Stesso ruolo di `ConfermaService`: chi chiama scrive un
 * `if` invece di aprire finestre e sottoscrivere risultati.
 *
 * Sta nella sezione dipendenti e non in shared perché è specifico del contratto:
 * l'unica cosa condivisa qui è il modo di lavorare, non la finestra.
 */
@Injectable({ providedIn: 'root' })
export class RinnovoDialogService {
  private readonly dialog = inject(MatDialog);

  public async chiedi(dipendente: DipendenteModel): Promise<string | undefined> {
    const dati: RinnovoDialogDataInterface = {
      nominativo: `${dipendente.cognome} ${dipendente.nome}`,
      dataScadenzaAttuale: dipendente.dataScadenza,
    };

    const riferimento = this.dialog.open<RinnovoDialog, RinnovoDialogDataInterface, string>(
      RinnovoDialog,
      {
        data: dati,
        width: LARGHEZZA,
        // Sui telefoni la larghezza fissa sfonderebbe lo schermo.
        maxWidth: 'calc(100vw - 2rem)',
      },
    );

    // `afterClosed()` emette undefined anche con Esc o cliccando fuori: in quel
    // caso non c'è nessuna data e non si rinnova niente.
    return (await firstValueFrom(riferimento.afterClosed())) ?? undefined;
  }
}
