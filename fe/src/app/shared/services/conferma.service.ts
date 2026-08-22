import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';
import { ConfermaDialog } from '../components/conferma-dialog/conferma-dialog';
import { ConfermaDialogDataInterface } from '../components/conferma-dialog/interfaces/conferma-dialog-data.interface';

/** Larghezza fissa: i testi sono due righe, una finestra più larga sembrerebbe vuota. */
const LARGHEZZA = '26rem';

/**
 * Unico modo per chiedere conferma di un'azione. Chi chiama non tocca MatDialog e
 * non conosce il componente: passa i testi e riceve un booleano, quindi la logica
 * resta un `if` normale invece di una sottoscrizione.
 */
@Injectable({ providedIn: 'root' })
export class ConfermaService {
  private readonly dialog = inject(MatDialog);

  /** `false` anche quando si chiude con Esc o cliccando fuori: nel dubbio non si procede. */
  public async chiedi(dati: ConfermaDialogDataInterface): Promise<boolean> {
    const riferimento = this.dialog.open<ConfermaDialog, ConfermaDialogDataInterface, boolean>(
      ConfermaDialog,
      {
        data: dati,
        width: LARGHEZZA,
        // Sui telefoni la larghezza fissa sfonderebbe lo schermo.
        maxWidth: 'calc(100vw - 2rem)',
      },
    );

    return (await firstValueFrom(riferimento.afterClosed())) ?? false;
  }
}
