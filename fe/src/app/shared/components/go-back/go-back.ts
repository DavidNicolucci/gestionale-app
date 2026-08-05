import {ChangeDetectionStrategy, Component, inject, input} from '@angular/core';
import {Router} from '@angular/router';
import {MatButton} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';
import {AppRoute} from '../../enums/app-route.enum';

/**
 * Pulsante "Indietro" con destinazione esplicita: la pagina dichiara dove si torna,
 * invece di dipendere da come l'utente ci è arrivato. Senza `to` resta il
 * comportamento del browser, da usare solo quando la destinazione non è nota.
 */
@Component({
  selector: 'app-go-back',
  imports: [MatButton, MatIcon],
  templateUrl: './go-back.html',
  styleUrl: './go-back.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoBack {
  /** Rotta di destinazione: se manca si torna alla pagina precedente. */
  readonly to = input<AppRoute>();

  /** Testo del pulsante, personalizzabile dalle pagine che non tornano a un elenco. */
  readonly etichetta = input('Indietro');

  private readonly router = inject(Router);

  /** Confronto con `undefined` e non falsy: `AppRoute.HOME` è la stringa vuota. */
  protected onClick(): void {
    const to = this.to();

    if (to === undefined) {
      window.history.back();
      return;
    }

    void this.router.navigate([to]);
  }
}
