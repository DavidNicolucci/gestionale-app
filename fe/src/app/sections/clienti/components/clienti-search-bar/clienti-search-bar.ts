import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatFormField, MatInput, MatLabel, MatPrefix, MatSuffix } from '@angular/material/input';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatOption } from '@angular/material/core';
import { ClientiFiltriModel } from '../../interfaces/clienti-filtri.model';

/**
 * Casella di ricerca e filtri, senza logica: mostra i valori che riceve dal padre
 * e gli notifica ogni modifica. Debounce, chiamata al backend e gestione degli
 * errori stanno nel componente pagina.
 */
@Component({
  selector: 'app-clienti-search-bar',
  imports: [
    MatFormField,
    MatLabel,
    MatInput,
    MatPrefix,
    MatSuffix,
    MatIcon,
    MatIconButton,
    MatProgressSpinner,
    MatAutocomplete,
    MatAutocompleteTrigger,
    MatOption,
  ],
  templateUrl: './clienti-search-bar.html',
  styleUrl: './clienti-search-bar.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientiSearchBar {
  /** Valori mostrati nei campi: lo stato lo tiene il padre. */
  readonly filtri = input.required<ClientiFiltriModel>();

  /** Valori proposti nelle tendine dei due filtri, calcolati dal padre sui risultati. */
  readonly opzioniRagioneSociale = input<string[]>([]);
  readonly opzioniPartitaIva = input<string[]>([]);

  /** Mostra lo spinner al posto del pulsante di pulizia. */
  readonly loading = input(false);

  /** Filtri aggiornati, a ogni tasto: sta al padre decidere quando cercare. */
  readonly filtriChange = output<ClientiFiltriModel>();

  protected onCampo(campo: keyof ClientiFiltriModel, event: Event): void {
    const valore = (event.target as HTMLInputElement).value;
    this.filtriChange.emit({ ...this.filtri(), [campo]: valore });
  }

  /** Voce scelta dalla tendina: vale come se l'utente l'avesse scritta. */
  protected onOpzione(campo: keyof ClientiFiltriModel, valore: string): void {
    this.filtriChange.emit({ ...this.filtri(), [campo]: valore });
  }

  protected onPulisci(): void {
    this.filtriChange.emit({ ...this.filtri(), termine: '' });
  }
}
