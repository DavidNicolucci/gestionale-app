import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';
import { ClientApiService } from '../../../services/client-api.service';
import { ClienteModel } from '../../../interfaces/cliente.model';
import { NuovoClienteFormInterface } from '../interfaces/nuovo-cliente-form.interface';
import { SalvaClienteRequestModel } from '../models/salva-cliente-request.model';
import { ErrorResponseModel } from '../../../../../shared/interfaces/error-response.model';
import { NuovoClienteFormService } from './nuovo-cliente-form.service';

/**
 * Salvataggio del cliente: il componente gli passa il form e legge i segnali,
 * senza sapere che esiste un backend. Come gli altri servizi di pagina non è
 * `providedIn: 'root'`, così riaprendo il form non ci si trova addosso l'errore
 * del tentativo precedente.
 */
@Injectable()
export class NuovoClienteQueryService {
  /** Testi degli errori di salvataggio: li scrive e li usa solo questa classe. */
  private static readonly MESSAGGI = {
    NON_AUTORIZZATO: 'Non hai i permessi per creare un cliente',
    DUPLICATO: 'Esiste già un cliente con questi dati',
    DATI_NON_VALIDI: 'Controlla i campi segnalati',
    GENERICO: 'Salvataggio non riuscito, riprova',
  } as const;

  public readonly inSalvataggio = computed(() => this._inSalvataggio());
  public readonly errorMessage = computed(() => this._errorMessage());
  private readonly clientiApi = inject(ClientApiService);
  private readonly formService = inject(NuovoClienteFormService);
  private readonly _inSalvataggio = signal(false);
  private readonly _errorMessage = signal<string | null>(null);

  /**
   * Restituisce il cliente creato, oppure `null` se il salvataggio è fallito:
   * così la pagina decide se navigare guardando il valore, senza try/catch suo.
   */
  public async salvaCliente(
    form: FormGroup<NuovoClienteFormInterface>,
  ): Promise<ClienteModel | null> {
    this._inSalvataggio.set(true);
    this._errorMessage.set(null);

    try {
      return await this.clientiApi.crea(SalvaClienteRequestModel.generateModel(form));
    } catch (errore) {
      this.gestisciErrore(errore, form);
      return null;
    } finally {
      // Nel finally: il pulsante deve tornare cliccabile anche quando va male.
      this._inSalvataggio.set(false);
    }
  }

  /**
   * Sul 400 di validazione i messaggi finiscono sotto ai campi (`fieldErrors`) e
   * in cima resta solo l'invito a controllarli; negli altri casi c'è solo il
   * messaggio di pagina.
   */
  private gestisciErrore(errore: unknown, form: FormGroup<NuovoClienteFormInterface>): void {
    if (!(errore instanceof HttpErrorResponse)) {
      this._errorMessage.set(NuovoClienteQueryService.MESSAGGI.GENERICO);
      return;
    }

    const body = errore.error as ErrorResponseModel | null;

    if (errore.status === 400 && body?.fieldErrors) {
      this.formService.applicaErroriBackend(form, body.fieldErrors);
      this._errorMessage.set(NuovoClienteQueryService.MESSAGGI.DATI_NON_VALIDI);
      return;
    }

    this._errorMessage.set(this.messaggioPerStato(errore.status));
  }

  private messaggioPerStato(status: number): string {
    switch (status) {
      case 401:
      case 403:
        return NuovoClienteQueryService.MESSAGGI.NON_AUTORIZZATO;
      case 409:
        return NuovoClienteQueryService.MESSAGGI.DUPLICATO;
      default:
        return NuovoClienteQueryService.MESSAGGI.GENERICO;
    }
  }
}
