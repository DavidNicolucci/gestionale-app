import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { MessaggioChatModel } from '../interfaces/messaggio-chat.model';

@Injectable({ providedIn: 'root' })
export class AssistenteApiService {
  private readonly http = inject(HttpClient);

  /** Conversazione già avvenuta, dalla più vecchia alla più recente. */
  async storico(): Promise<MessaggioChatModel[]> {
    const get$ = this.http.get<MessaggioChatModel[]>('/api/chat/messaggi');
    return plainToInstance(MessaggioChatModel, (await firstValueFrom(get$)) ?? []);
  }

  /**
   * Manda la domanda e restituisce la risposta dell'assistente. La domanda la
   * salva il backend: qui non serve rimandarla.
   *
   * La chiamata resta aperta per tutta l'elaborazione, compresi gli strumenti
   * che il modello usa per leggere dal database: può volerci qualche secondo.
   */
  async invia(domanda: string): Promise<MessaggioChatModel> {
    const post$ = this.http.post<MessaggioChatModel>('/api/chat', { domanda });
    return plainToInstance(MessaggioChatModel, await firstValueFrom(post$));
  }
}
