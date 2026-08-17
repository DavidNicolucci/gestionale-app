import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { ACCEPT_EXCEL, ESTENSIONI_EXCEL } from '../../constants/import-timesheet.constants';

/** Soglia oltre la quale la dimensione si legge meglio in MB che in KB. */
const BYTE_PER_MB = 1024 * 1024;

/**
 * Riquadro di caricamento del file, senza logica: mostra quello che riceve dal
 * padre e gli notifica il file scelto. Validazione, chiamata al backend e
 * gestione degli errori stanno nel query service della pagina.
 */
@Component({
  selector: 'app-timesheet-upload',
  imports: [MatButton, MatIconButton, MatIcon],
  templateUrl: './timesheet-upload.html',
  styleUrl: './timesheet-upload.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TimesheetUpload {
  /** File pronto per l'invio: lo stato lo tiene il padre, che lo ha già validato. */
  readonly file = input<File | null>(null);

  /** Upload in corso: blocca la scelta di un altro file e il secondo invio. */
  readonly inCaricamento = input(false);

  /** File scelto o trascinato, `null` quando viene tolto: sta al padre validarlo. */
  readonly fileChange = output<File | null>();

  /** Click su "Importa": è il padre a parlare col backend. */
  readonly importa = output<void>();

  protected readonly accept = ACCEPT_EXCEL;
  protected readonly estensioni = ESTENSIONI_EXCEL.join(', ');

  /** Dimensione del file scelto in forma leggibile, accanto al nome. */
  protected readonly dimensione = computed(() => this.formattaDimensione(this.file()?.size));

  /** Vero mentre un file è sopra al riquadro: serve solo a evidenziarlo. */
  protected readonly inTrascinamento = signal(false);

  private readonly inputFile = viewChild.required<ElementRef<HTMLInputElement>>('inputFile');

  protected onScegli(): void {
    this.inputFile().nativeElement.click();
  }

  protected onInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.fileChange.emit(input.files?.[0] ?? null);
    // Svuotare l'input è quello che permette di riselezionare lo stesso file:
    // senza, il browser non emette 'change' perché il valore non è cambiato.
    input.value = '';
  }

  /**
   * Senza preventDefault il browser aprirebbe il file al posto di lasciarlo
   * cadere nel riquadro: va fatto sia sul dragover sia sul drop.
   */
  protected onDragOver(event: DragEvent): void {
    event.preventDefault();

    if (!this.inCaricamento()) {
      this.inTrascinamento.set(true);
    }
  }

  protected onDragLeave(): void {
    this.inTrascinamento.set(false);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.inTrascinamento.set(false);

    if (this.inCaricamento()) {
      return;
    }

    // Solo il primo: il backend importa un file per richiesta.
    this.fileChange.emit(event.dataTransfer?.files?.[0] ?? null);
  }

  protected onRimuovi(): void {
    this.fileChange.emit(null);
  }

  private formattaDimensione(byte: number | undefined): string {
    if (byte === undefined) {
      return '';
    }

    return byte >= BYTE_PER_MB
      ? `${(byte / BYTE_PER_MB).toFixed(1)} MB`
      : `${Math.max(1, Math.round(byte / 1024))} KB`;
  }
}
