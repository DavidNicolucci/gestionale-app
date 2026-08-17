import { AutoreChat } from '../enums/autore-chat.enum';

/** Un messaggio della conversazione (`ChatMessaggioResponse`). */
export class MessaggioChatModel {
  /**
   * Id assegnato dal backend. È `null` sul messaggio appena scritto dall'utente:
   * viene mostrato subito, prima che il server risponda e gli dia un id.
   */
  id: number | null = null;
  autore!: AutoreChat;
  testo!: string;
  /** ISO 8601 in UTC, come lo serializza l'`Instant` del backend. */
  istante!: string;

  /** Messaggio locale, per far comparire la domanda senza aspettare la risposta. */
  static dellUtente(testo: string): MessaggioChatModel {
    const messaggio = new MessaggioChatModel();
    messaggio.autore = AutoreChat.UTENTE;
    messaggio.testo = testo;
    messaggio.istante = new Date().toISOString();
    return messaggio;
  }
}
