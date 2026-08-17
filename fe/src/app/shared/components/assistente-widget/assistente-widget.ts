import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatFabButton, MatIconButton } from '@angular/material/button';
import { AssistenteThread } from './components/assistente-thread/assistente-thread';
import { AssistenteStateService } from '../../../services/assistente/service/assistente-state.service';
import { AuthService } from '../../../services/auth/service/auth.service';

/**
 * Assistente AI sempre a portata di click: un pulsante fisso in basso a destra
 * che apre la chat sopra alla pagina.
 *
 * Sta montato una volta sola, dentro al guscio delle pagine autenticate, e non
 * dentro alle singole sezioni: così la conversazione non si interrompe quando si
 * naviga, e nessuno deve ricordarsi di aggiungerlo alle pagine nuove.
 */
@Component({
  selector: 'app-assistente-widget',
  imports: [MatIcon, MatFabButton, MatIconButton, AssistenteThread],
  templateUrl: './assistente-widget.html',
  styleUrl: './assistente-widget.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssistenteWidget {
  protected readonly stato = inject(AssistenteStateService);

  /** Con un ruolo che non può usare `/api/chat` il pulsante non compare proprio. */
  protected readonly abilitato = inject(AuthService).puoUsareAssistente;

  /** Testo in fase di scrittura: vive qui, non nello stato condiviso. */
  protected readonly bozza = signal('');

  private readonly campo = viewChild<ElementRef<HTMLTextAreaElement>>('campo');

  constructor() {
    // All'apertura il cursore è già nel campo: aprire la chat e dover cliccare
    // per scrivere sarebbe un passaggio in più a ogni domanda.
    effect(() => {
      if (this.stato.aperto()) {
        requestAnimationFrame(() => this.campo()?.nativeElement.focus());
      }
    });
  }

  protected onAlterna(): void {
    this.stato.alterna();
  }

  protected onBozza(event: Event): void {
    this.bozza.set((event.target as HTMLTextAreaElement).value);
  }

  /** Invio manda, Maiusc+Invio va a capo: come in qualunque chat. */
  protected onTasto(event: KeyboardEvent): void {
    if (event.key !== 'Enter' || event.shiftKey) {
      return;
    }

    event.preventDefault();
    void this.onInvia();
  }

  protected async onInvia(): Promise<void> {
    const domanda = this.bozza();

    if (!domanda.trim() || this.stato.inAttesa()) {
      return;
    }

    // Svuotato subito: il campo deve tornare libero mentre si aspetta.
    this.bozza.set('');
    await this.stato.invia(domanda);
  }

  /** Domanda proposta: vale come se l'utente l'avesse scritta e inviata. */
  protected async onSuggerimento(domanda: string): Promise<void> {
    await this.stato.invia(domanda);
  }
}
