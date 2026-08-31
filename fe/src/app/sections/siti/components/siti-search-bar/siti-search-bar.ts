import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatFormField, MatInput, MatLabel, MatPrefix, MatSuffix } from '@angular/material/input';
import { MatIcon } from '@angular/material/icon';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatOption } from '@angular/material/core';
import { MatSelect, MatSelectChange } from '@angular/material/select';
import { SitiFiltriModel } from '../../interfaces/siti-filtri.model';
import { ClienteModel } from '../../../clienti/interfaces/cliente.model';

/**
 * Casella di ricerca e filtri, senza logica: mostra i valori che riceve dal padre
 * e gli notifica ogni modifica. Debounce, chiamata al backend e gestione degli
 * errori stanno nel componente pagina.
 */
@Component({
  selector: 'app-siti-search-bar',
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
    MatSelect,
  ],
  templateUrl: './siti-search-bar.html',
  styleUrl: './siti-search-bar.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SitiSearchBar {
  /** Valori mostrati nei campi: lo stato lo tiene il padre. */
  readonly filtri = input.required<SitiFiltriModel>();

  /** Voci proposte nella tendina del nome, calcolate dal padre sui risultati. */
  readonly opzioniNome = input<string[]>([]);

  /**
   * Clienti su cui si può filtrare. È una tendina a scelta fissa e non un campo
   * di testo: il backend confronta l'id, quindi un nome scritto a mano non
   * filtrerebbe niente.
   */
  readonly clienti = input<ClienteModel[]>([]);

  /** Filtri aggiornati, a ogni tasto: sta al padre decidere quando cercare. */
  readonly filtriChange = output<SitiFiltriModel>();

  /** Click su "Applica filtri": il padre porta i risultati in tabella. */
  readonly applica = output<void>();

  /** Click su "Rimuovi filtri": il padre svuota campi e tabella. */
  readonly rimuovi = output<void>();

  /** Con tutti i campi vuoti non c'è niente da applicare né da rimuovere. */
  protected readonly haFiltri = computed(() => {
    const filtri = this.filtri();
    return !!(filtri.termine.trim() || filtri.nome.trim() || filtri.clienteId !== null);
  });

  protected onCampo(campo: 'termine' | 'nome', event: Event): void {
    const valore = (event.target as HTMLInputElement).value;
    this.filtriChange.emit({ ...this.filtri(), [campo]: valore });
  }

  /** Voce scelta dalla tendina: vale come se l'utente l'avesse scritta. */
  protected onOpzione(campo: 'termine' | 'nome', valore: string): void {
    this.filtriChange.emit({ ...this.filtri(), [campo]: valore });
  }

  /** La voce "Tutti i clienti" ha valore `null`: è l'assenza di filtro. */
  protected onCliente(evento: MatSelectChange): void {
    this.filtriChange.emit({
      ...this.filtri(),
      clienteId: (evento.value as number | null) ?? null,
    });
  }

  protected onPulisci(): void {
    this.filtriChange.emit({ ...this.filtri(), termine: '' });
  }
}
