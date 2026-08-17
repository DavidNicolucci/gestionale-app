import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  input,
  output,
  viewChild,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MessaggioChatModel } from '../../../../../services/assistente/interfaces/messaggio-chat.model';
import { AutoreChat } from '../../../../../services/assistente/enums/autore-chat.enum';
import { SUGGERIMENTI_CHAT } from '../../constants/suggerimenti.constant';

/**
 * Elenco dei messaggi, senza logica: mostra quello che riceve e segnala i click
 * sui suggerimenti. L'unica cosa che fa da sé è tenersi in fondo.
 */
@Component({
  selector: 'app-assistente-thread',
  imports: [DatePipe, MatProgressSpinner],
  templateUrl: './assistente-thread.html',
  styleUrl: './assistente-thread.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssistenteThread {
  readonly messaggi = input.required<MessaggioChatModel[]>();

  /** Risposta in arrivo: al fondo compaiono i puntini. */
  readonly inAttesa = input(false);

  /** Storico in arrivo, solo alla prima apertura. */
  readonly inCaricamento = input(false);

  readonly errorMessage = input<string | null>(null);

  /** Click su una domanda proposta: la manda il padre, come se fosse stata scritta. */
  readonly suggerimento = output<string>();

  protected readonly AutoreChat = AutoreChat;
  protected readonly suggerimenti = SUGGERIMENTI_CHAT;

  private readonly elenco = viewChild.required<ElementRef<HTMLDivElement>>('elenco');

  constructor() {
    // Ogni messaggio nuovo (e i puntini dell'attesa) riporta la vista in fondo,
    // altrimenti la risposta arriverebbe fuori schermo. requestAnimationFrame:
    // va fatto dopo che il DOM è stato aggiornato, sennò scrollHeight è ancora
    // quello di prima e lo scroll si ferma un messaggio indietro.
    effect(() => {
      this.messaggi();
      this.inAttesa();
      requestAnimationFrame(() => this.vaiInFondo());
    });
  }

  private vaiInFondo(): void {
    const elemento = this.elenco().nativeElement;
    elemento.scrollTop = elemento.scrollHeight;
  }
}
