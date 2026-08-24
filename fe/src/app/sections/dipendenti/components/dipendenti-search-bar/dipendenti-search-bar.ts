import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatFormField, MatInput, MatLabel, MatPrefix, MatSuffix } from '@angular/material/input';
import { MatIcon } from '@angular/material/icon';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatOption } from '@angular/material/core';
import { MatSlideToggle, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { DipendentiFiltriModel } from '../../interfaces/dipendenti-filtri.model';

/**
 * Casella di ricerca e filtri, senza logica: mostra i valori che riceve dal padre
 * e gli notifica ogni modifica. Debounce, chiamata al backend e gestione degli
 * errori stanno nel componente pagina.
 */
@Component({
  selector: 'app-dipendenti-search-bar',
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
    MatSlideToggle,
  ],
  templateUrl: './dipendenti-search-bar.html',
  styleUrl: './dipendenti-search-bar.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DipendentiSearchBar {
  /** Valori mostrati nei campi: lo stato lo tiene il padre. */
  readonly filtri = input.required<DipendentiFiltriModel>();

  /** Valori proposti nelle tendine, calcolati dal padre sui risultati. */
  readonly opzioniNome = input<string[]>([]);
  readonly opzioniCognome = input<string[]>([]);
  readonly opzioniCodiceFiscale = input<string[]>([]);

  /** Filtri aggiornati, a ogni tasto: sta al padre decidere quando cercare. */
  readonly filtriChange = output<DipendentiFiltriModel>();

  /** Click su "Applica filtri": il padre porta i risultati in tabella. */
  readonly applica = output<void>();

  /** Click su "Rimuovi filtri": il padre svuota campi e tabella. */
  readonly rimuovi = output<void>();

  /**
   * L'interruttore "Mostra eliminati" ha un evento suo e non passa da `filtriChange`:
   * gli altri campi aspettano "Applica filtri", questo vale subito. Un interruttore
   * che resta acceso senza che cambi niente a schermo si legge come rotto.
   */
  readonly includiEliminatiChange = output<boolean>();

  /**
   * Con tutti i campi vuoti non c'è niente da applicare né da rimuovere.
   * L'interruttore non conta: si applica da sé, e "Rimuovi filtri" lo spegne
   * insieme al resto solo perché fa parte dei filtri.
   */
  protected readonly haFiltri = computed(() => {
    const filtri = this.filtri();
    return !!(
      filtri.termine.trim() ||
      filtri.nome.trim() ||
      filtri.cognome.trim() ||
      filtri.codiceFiscale.trim()
    );
  });

  protected onCampo(campo: keyof DipendentiFiltriModel, event: Event): void {
    const valore = (event.target as HTMLInputElement).value;
    this.filtriChange.emit({ ...this.filtri(), [campo]: valore });
  }

  /** Voce scelta dalla tendina: vale come se l'utente l'avesse scritta. */
  protected onOpzione(campo: keyof DipendentiFiltriModel, valore: string): void {
    this.filtriChange.emit({ ...this.filtri(), [campo]: valore });
  }

  protected onIncludiEliminati(evento: MatSlideToggleChange): void {
    this.includiEliminatiChange.emit(evento.checked);
  }

  protected onPulisci(): void {
    this.filtriChange.emit({ ...this.filtri(), termine: '' });
  }
}
