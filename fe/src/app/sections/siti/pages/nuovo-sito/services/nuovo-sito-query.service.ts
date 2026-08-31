import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';
import { SitoApiService } from '../../../services/sito-api.service';
import { SitoModel } from '../../../interfaces/sito.model';
import { NuovoSitoFormInterface } from '../interfaces/nuovo-sito-form.interface';
import { SalvaSitoRequestModel } from '../models/salva-sito-request.model';
import { ClienteModel } from '../../../../clienti/interfaces/cliente.model';
import { ClientApiService } from '../../../../clienti/services/client-api.service';
import { ErrorResponseModel } from '../../../../../shared/interfaces/error-response.model';
import { NuovoSitoFormService } from './nuovo-sito-form.service';

/**
 * Salvataggio del sito e anagrafica clienti per la tendina: il componente gli
 * passa il form e legge i segnali, senza sapere che esiste un backend. Come gli
 * altri servizi di pagina non è `providedIn: 'root'`, così riaprendo il form non
 * ci si trova addosso l'errore del tentativo precedente.
 */
@Injectable()
export class NuovoSitoQueryService {
  /**
   * Testi degli errori di salvataggio: li scrive e li usa solo questa classe.
   * Il 404 è il caso concreto della sezione: il sito nasce attaccato a un cliente,
   * e quel cliente può essere stato eliminato mentre il form era aperto.
   */
  private static readonly MESSAGGI = {
    NON_AUTORIZZATO: 'Non hai i permessi per creare un sito',
    CLIENTE_INESISTENTE: 'Il cliente scelto non esiste più: ricarica la pagina',
    DATI_NON_VALIDI: 'Controlla i campi segnalati',
    GENERICO: 'Salvataggio non riuscito, riprova',
    CLIENTI_NON_CARICATI: 'Non è stato possibile caricare i clienti, ricarica la pagina',
  } as const;

  public readonly inSalvataggio = computed(() => this._inSalvataggio());
  public readonly errorMessage = computed(() => this._errorMessage());
  /** Clienti fra cui scegliere: senza almeno uno non si può creare un sito. */
  public readonly clienti = computed(() => this._clienti());
  public readonly inCaricamentoClienti = computed(() => this._inCaricamentoClienti());
  private readonly sitiApi = inject(SitoApiService);
  private readonly clientiApi = inject(ClientApiService);
  private readonly formService = inject(NuovoSitoFormService);
  private readonly _inSalvataggio = signal(false);
  private readonly _errorMessage = signal<string | null>(null);
  private readonly _clienti = signal<ClienteModel[]>([]);
  private readonly _inCaricamentoClienti = signal(true);

  constructor() {
    void this.caricaClienti();
  }

  /**
   * Restituisce il sito creato, oppure `null` se il salvataggio è fallito: così
   * la pagina decide se navigare guardando il valore, senza try/catch suo.
   */
  public async salvaSito(form: FormGroup<NuovoSitoFormInterface>): Promise<SitoModel | null> {
    this._inSalvataggio.set(true);
    this._errorMessage.set(null);

    try {
      return await this.sitiApi.crea(SalvaSitoRequestModel.generateModel(form));
    } catch (errore) {
      this.gestisciErrore(errore, form);
      return null;
    } finally {
      // Nel finally: il pulsante deve tornare cliccabile anche quando va male.
      this._inSalvataggio.set(false);
    }
  }

  /**
   * Anagrafica clienti per la tendina. Qui l'errore si mostra, al contrario di
   * quello dell'elenco: senza clienti il form non è compilabile, e lasciare una
   * tendina vuota senza spiegazioni sembrerebbe un'anagrafica vuota.
   */
  private async caricaClienti(): Promise<void> {
    this._inCaricamentoClienti.set(true);

    try {
      this._clienti.set(await this.clientiApi.lista());
    } catch {
      this._clienti.set([]);
      this._errorMessage.set(NuovoSitoQueryService.MESSAGGI.CLIENTI_NON_CARICATI);
    } finally {
      this._inCaricamentoClienti.set(false);
    }
  }

  /**
   * Sul 400 di validazione i messaggi finiscono sotto ai campi (`fieldErrors`) e
   * in cima resta solo l'invito a controllarli; negli altri casi c'è solo il
   * messaggio di pagina.
   */
  private gestisciErrore(errore: unknown, form: FormGroup<NuovoSitoFormInterface>): void {
    if (!(errore instanceof HttpErrorResponse)) {
      this._errorMessage.set(NuovoSitoQueryService.MESSAGGI.GENERICO);
      return;
    }

    const body = errore.error as ErrorResponseModel | null;

    if (errore.status === 400 && body?.fieldErrors) {
      this.formService.applicaErroriBackend(form, body.fieldErrors);
      this._errorMessage.set(NuovoSitoQueryService.MESSAGGI.DATI_NON_VALIDI);
      return;
    }

    this._errorMessage.set(this.messaggioPerStato(errore.status));
  }

  private messaggioPerStato(status: number): string {
    switch (status) {
      case 401:
      case 403:
        return NuovoSitoQueryService.MESSAGGI.NON_AUTORIZZATO;
      // Il backend risponde 404 quando l'id del cliente non trova niente: non è
      // il sito a mancare, è il cliente a cui lo si sta attaccando.
      case 404:
        return NuovoSitoQueryService.MESSAGGI.CLIENTE_INESISTENTE;
      default:
        return NuovoSitoQueryService.MESSAGGI.GENERICO;
    }
  }
}
