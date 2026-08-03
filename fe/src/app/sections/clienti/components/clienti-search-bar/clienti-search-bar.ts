import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatFormField, MatInput, MatLabel, MatPrefix, MatSuffix } from '@angular/material/input';
import { MatIcon } from '@angular/material/icon';
import { MatButton, MatIconButton } from '@angular/material/button';
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
    MatButton,
    MatIconButton,
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

  /** Filtri aggiornati, a ogni tasto: sta al padre decidere quando cercare. */
  readonly filtriChange = output<ClientiFiltriModel>();

  /** Click su "Applica filtri": il padre porta i risultati in tabella. */
  readonly applica = output<void>();

  /** Click su "Rimuovi filtri": il padre svuota campi e tabella. */
  readonly rimuovi = output<void>();

  /** Con tutti i campi vuoti non c'è niente da applicare né da rimuovere. */
  protected readonly haFiltri = computed(() => {
    const filtri = this.filtri();
    return !!(filtri.termine.trim() || filtri.ragioneSociale.trim() || filtri.partitaIva.trim());
  });

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
