import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';
import { DipendenteApiService } from '../../../services/dipendente-api.service';
import { DipendenteModel } from '../../../interfaces/dipendente.model';
import { DipendenteFormInterface } from '../../../interfaces/dipendente-form.interface';
import { SalvaDipendenteRequestModel } from '../models/salva-dipendente-request.model';
import { ErrorResponseModel } from '../../../../../shared/interfaces/error-response.model';
import { DipendenteFormService } from '../../../services/dipendente-form.service';

/**
 * Salvataggio del dipendente: il componente gli passa il form e legge i segnali,
 * senza sapere che esiste un backend. Come gli altri servizi di pagina non è
 * `providedIn: 'root'`, così riaprendo il form non ci si trova addosso l'errore
 * del tentativo precedente.
 */
@Injectable()
export class NuovoDipendenteQueryService {
  /**
   * Testi degli errori di salvataggio: li scrive e li usa solo questa classe.
   * Il 409 è il caso concreto della sezione: il codice fiscale è UNIQUE sul
   * database e il service lo controlla anche prima di scrivere.
   */
  private static readonly MESSAGGI = {
    NON_AUTORIZZATO: 'Non hai i permessi per creare un dipendente',
    DUPLICATO: 'Esiste già un dipendente con questo codice fiscale',
    DATI_NON_VALIDI: 'Controlla i campi segnalati',
    GENERICO: 'Salvataggio non riuscito, riprova',
  } as const;

  public readonly inSalvataggio = computed(() => this._inSalvataggio());
  public readonly errorMessage = computed(() => this._errorMessage());
  private readonly dipendentiApi = inject(DipendenteApiService);
  private readonly formService = inject(DipendenteFormService);
  private readonly _inSalvataggio = signal(false);
  private readonly _errorMessage = signal<string | null>(null);

  /**
   * Restituisce il dipendente creato, oppure `null` se il salvataggio è fallito:
   * così la pagina decide se navigare guardando il valore, senza try/catch suo.
   */
  public async salvaDipendente(
    form: FormGroup<DipendenteFormInterface>,
  ): Promise<DipendenteModel | null> {
    this._inSalvataggio.set(true);
    this._errorMessage.set(null);

    try {
      return await this.dipendentiApi.crea(SalvaDipendenteRequestModel.generateModel(form));
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
  private gestisciErrore(errore: unknown, form: FormGroup<DipendenteFormInterface>): void {
    if (!(errore instanceof HttpErrorResponse)) {
      this._errorMessage.set(NuovoDipendenteQueryService.MESSAGGI.GENERICO);
      return;
    }

    const body = errore.error as ErrorResponseModel | null;

    if (errore.status === 400 && body?.fieldErrors) {
      this.formService.applicaErroriBackend(form, body.fieldErrors);
      this._errorMessage.set(NuovoDipendenteQueryService.MESSAGGI.DATI_NON_VALIDI);
      return;
    }

    this._errorMessage.set(this.messaggioPerStato(errore.status));
  }

  private messaggioPerStato(status: number): string {
    switch (status) {
      case 401:
      case 403:
        return NuovoDipendenteQueryService.MESSAGGI.NON_AUTORIZZATO;
      case 409:
        return NuovoDipendenteQueryService.MESSAGGI.DUPLICATO;
      default:
        return NuovoDipendenteQueryService.MESSAGGI.GENERICO;
    }
  }
}
