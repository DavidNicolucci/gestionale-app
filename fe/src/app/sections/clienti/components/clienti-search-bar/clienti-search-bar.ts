import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatFormField, MatInput, MatLabel, MatPrefix, MatSuffix } from '@angular/material/input';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { MatProgressSpinner } from '@angular/material/progress-spinner';

/**
 * Casella di ricerca senza logica: mostra il testo che riceve dal padre e gli
 * notifica ogni modifica. Debounce, chiamata al backend e gestione degli errori
 * stanno nel componente pagina.
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
  ],
  templateUrl: './clienti-search-bar.html',
  styleUrl: './clienti-search-bar.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientiSearchBar {

  readonly termine = input('');
  readonly loading = input(false);
  readonly ricerca = output<string>();

  protected onInput(event: Event): void {
    this.ricerca.emit((event.target as HTMLInputElement).value);
  }

  protected onPulisci(): void {
    this.ricerca.emit('');
  }
}
