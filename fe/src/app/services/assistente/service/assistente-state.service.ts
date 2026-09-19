import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AssistenteApiService } from './assistente-api.service';
import { MessaggioChatModel } from '../interfaces/messaggio-chat.model';
import { ErrorResponseModel } from '../../../shared/interfaces/error-response.model';
import { AuthService } from '../../auth/service/auth.service';

/**
 * Stato della conversazione con l'assistente.
 *
 * È l'unico servizio di questo progetto tenuto in `root` invece che sul
 * componente: la chat si apre da qualunque pagina e deve restare aperta mentre
 * si naviga, quindi non può vivere quanto una pagina. Lo stato non si accumula
 * comunque all'infinito, perché segue la sessione: si riempie al login e si
 * svuota all'uscita.
 */
@Injectable({ providedIn: 'root' })
export class AssistenteStateService {
  private static readonly MESSAGGI = {
    NON_AUTORIZZATO: "Non hai i permessi per usare l'assistente",
    STORICO: 'Non sono riuscito a recuperare la conversazione precedente',
    GENERICO: 'Nessuna risposta, riprova',
    TROPPE_DOMANDE: 'Hai raggiunto il limite di domande. Riprova più tardi',
  } as const;

  public readonly aperto = computed(() => this._aperto());
  public readonly messaggi = computed(() => this._messaggi());
  /** In attesa della risposta: la chiamata è sincrona e può durare secondi. */
  public readonly inAttesa = computed(() => this._inAttesa());
  public readonly errorMessage = computed(() => this._errorMessage());
  /** Vero solo alla prima apertura della sessione, mentre arriva lo storico. */
  public readonly inCaricamento = computed(() => this._inCaricamento());
  public readonly isVuota = computed(() => this._messaggi().length === 0);

  private readonly api = inject(AssistenteApiService);
  private readonly auth = inject(AuthService);
  private readonly _aperto = signal(false);
  private readonly _messaggi = signal<MessaggioChatModel[]>([]);
  private readonly _inAttesa = signal(false);
  private readonly _inCaricamento = signal(false);
  private readonly _errorMessage = signal<string | null>(null);

  constructor() {
    // Un solo punto per le due metà del ciclo di vita: la conversazione arriva
    // quando c'è una sessione e sparisce quando non c'è più. Legandolo qui invece
    // che al pulsante "Esci", vale anche per la sessione scaduta e per il logout
    // fatto da un'altra pagina.
    effect(() => {
      if (this.auth.isAuthenticated() && this.auth.puoUsareAssistente()) {
        void this.caricaStorico();
        return;
      }

      this.reset();
    });
  }

  public alterna(): void {
    this._aperto.update((aperto) => !aperto);
  }

  public chiudi(): void {
    this._aperto.set(false);
  }

  /**
   * Manda la domanda. Il messaggio dell'utente compare subito, senza aspettare
   * il server: l'attesa è lunga e vedere la propria domanda sparire nel vuoto
   * darebbe l'impressione che il click non abbia funzionato.
   */
  public async invia(domanda: string): Promise<void> {
    const testo = domanda.trim();

    if (!testo || this._inAttesa()) {
      return;
    }

    this._messaggi.update((messaggi) => [...messaggi, MessaggioChatModel.dellUtente(testo)]);
    this._inAttesa.set(true);
    this._errorMessage.set(null);

    try {
      const risposta = await this.api.invia(testo);
      this._messaggi.update((messaggi) => [...messaggi, risposta]);
    } catch (errore) {
      // La domanda resta a schermo, così si può riformularla senza riscriverla da
      // capo. Il backend l'ha anche salvata, tranne quando risponde 429: lì non è
      // mai partita, e ricaricando la pagina non si ritrova.
      this._errorMessage.set(this.messaggioPerErrore(errore));
    } finally {
      this._inAttesa.set(false);
    }
  }

  private async caricaStorico(): Promise<void> {
    this._inCaricamento.set(true);
    this._errorMessage.set(null);

    try {
      this._messaggi.set(await this.api.storico());
    } catch {
      // Non blocca la chat: si può scrivere lo stesso, semplicemente senza
      // rivedere i messaggi vecchi.
      this._errorMessage.set(AssistenteStateService.MESSAGGI.STORICO);
    } finally {
      this._inCaricamento.set(false);
    }
  }

  /** Fine sessione: non deve restare niente in memoria per l'utente successivo. */
  private reset(): void {
    this._messaggi.set([]);
    this._errorMessage.set(null);
    this._inAttesa.set(false);
    this._aperto.set(false);
  }

  private messaggioPerErrore(errore: unknown): string {
    if (!(errore instanceof HttpErrorResponse)) {
      return AssistenteStateService.MESSAGGI.GENERICO;
    }

    switch (errore.status) {
      case 403:
        return AssistenteStateService.MESSAGGI.NON_AUTORIZZATO;
      case 429:
        // Il tetto alle domande (`chat.limite.*` sul backend). Qui il messaggio del
        // server vale più del nostro: dice fra quanto si libera il posto, e senza
        // quel dettaglio l'utente non sa se riprovare fra un minuto o domani.
        return this.messaggioDalServer(errore) ?? AssistenteStateService.MESSAGGI.TROPPE_DOMANDE;
      default:
        return AssistenteStateService.MESSAGGI.GENERICO;
    }
  }

  /** Il `message` di ErrorResponse, se c'è: il body potrebbe anche non essere il nostro. */
  private messaggioDalServer(errore: HttpErrorResponse): string | null {
    const messaggio = (errore.error as ErrorResponseModel | null)?.message;

    return typeof messaggio === 'string' && messaggio.trim() ? messaggio : null;
  }
}
