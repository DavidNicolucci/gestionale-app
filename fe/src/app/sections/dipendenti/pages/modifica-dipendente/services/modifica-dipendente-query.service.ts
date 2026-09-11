import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';
import { DipendenteApiService } from '../../../services/dipendente-api.service';
import { DipendenteModel } from '../../../interfaces/dipendente.model';
import { DipendenteFormInterface } from '../../../interfaces/dipendente-form.interface';
import { DipendenteFormService } from '../../../services/dipendente-form.service';
import { StatoDipendente } from '../../../enums/stato-dipendente.enum';
import { AggiornaDipendenteRequestModel } from '../models/aggiorna-dipendente-request.model';
import { ErrorResponseModel } from '../../../../../shared/interfaces/error-response.model';

/**
 * Caricamento e salvataggio della modifica: il componente gli passa l'id e il form
 * e legge i segnali, senza sapere che esiste un backend. Come gli altri servizi di
 * pagina non è `providedIn: 'root'`, così riaprendo la pagina non ci si trova
 * addosso l'errore del tentativo precedente.
 */
@Injectable()
export class ModificaDipendenteQueryService {
  private static readonly MESSAGGI_CARICAMENTO = {
    NON_AUTORIZZATO: 'Non hai i permessi per vedere questo dipendente',
    NON_TROVATO: 'Il dipendente non esiste più',
    GENERICO: 'Caricamento non riuscito, riprova',
  } as const;

  /**
   * Il 409 qui non è un doppione ma un "non si può ancora": il backend rifiuta la
   * modifica di uno scaduto o eliminato, che prima devono passare dal rinnovo o dal
   * ripristino. Sono due casi distinti e li distinguiamo, perché l'azione da fare è
   * diversa.
   */
  private static readonly MESSAGGI_SALVATAGGIO = {
    NON_AUTORIZZATO: 'Non hai i permessi per modificare un dipendente',
    NON_TROVATO: 'Il dipendente non esiste più',
    DUPLICATO: 'Esiste già un dipendente con questo codice fiscale',
    NON_MODIFICABILE:
      'Il dipendente non è più modificabile: il contratto è scaduto oppure è stato eliminato. Torna all’elenco, rinnovalo o ripristinalo, poi riprova.',
    DATI_NON_VALIDI: 'Controlla i campi segnalati',
    GENERICO: 'Salvataggio non riuscito, riprova',
  } as const;

  /** Perché un dipendente esistente non si può comunque modificare. */
  private static readonly MOTIVI_NON_MODIFICABILE: Partial<Record<StatoDipendente, string>> = {
    [StatoDipendente.SCADUTO]:
      'Il contratto di questo dipendente è scaduto: rinnovalo dall’elenco prima di modificarlo.',
    [StatoDipendente.ELIMINATO]:
      'Questo dipendente è eliminato: ripristinalo dall’elenco prima di modificarlo.',
  };

  public readonly dipendente = computed(() => this._dipendente());
  public readonly inCaricamento = computed(() => this._inCaricamento());
  public readonly inSalvataggio = computed(() => this._inSalvataggio());
  public readonly errorMessage = computed(() => this._errorMessage());
  /**
   * Valorizzato quando il dipendente esiste ma è in uno stato che il backend non
   * lascia modificare. Diverso da `errorMessage`: lì c'è una cosa andata storta, qui
   * una regola del dominio, e il form non va nemmeno mostrato.
   */
  public readonly bloccoModifica = computed(() => this._bloccoModifica());

  private readonly dipendentiApi = inject(DipendenteApiService);
  private readonly formService = inject(DipendenteFormService);
  private readonly _dipendente = signal<DipendenteModel | null>(null);
  private readonly _inCaricamento = signal(true);
  private readonly _inSalvataggio = signal(false);
  private readonly _errorMessage = signal<string | null>(null);
  private readonly _bloccoModifica = signal<string | null>(null);

  /**
   * Carica il dipendente e riempie il form. Torna il dipendente, oppure `null` se
   * non si è potuto caricare o non è modificabile: così la pagina decide cosa
   * mostrare guardando il valore, senza try/catch suo.
   */
  public async carica(
    id: number,
    form: FormGroup<DipendenteFormInterface>,
  ): Promise<DipendenteModel | null> {
    this._inCaricamento.set(true);
    this._errorMessage.set(null);
    this._bloccoModifica.set(null);

    try {
      const dipendente = await this.dipendentiApi.dettaglio(id);
      this._dipendente.set(dipendente);

      // Il controllo lo rifà comunque il backend al salvataggio: questo serve a non
      // far compilare un modulo che verrebbe rifiutato alla fine.
      const motivo = ModificaDipendenteQueryService.MOTIVI_NON_MODIFICABILE[dipendente.stato];
      if (motivo) {
        this._bloccoModifica.set(motivo);
        return null;
      }

      this.formService.popolaForm(form, dipendente);
      return dipendente;
    } catch (errore) {
      this._errorMessage.set(this.messaggioCaricamento(errore));
      return null;
    } finally {
      this._inCaricamento.set(false);
    }
  }

  /**
   * Restituisce il dipendente aggiornato, oppure `null` se il salvataggio è fallito.
   */
  public async salva(
    id: number,
    form: FormGroup<DipendenteFormInterface>,
  ): Promise<DipendenteModel | null> {
    this._inSalvataggio.set(true);
    this._errorMessage.set(null);

    try {
      return await this.dipendentiApi.aggiorna(id, AggiornaDipendenteRequestModel.generateModel(form));
    } catch (errore) {
      this.gestisciErroreSalvataggio(errore, form);
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
  private gestisciErroreSalvataggio(
    errore: unknown,
    form: FormGroup<DipendenteFormInterface>,
  ): void {
    if (!(errore instanceof HttpErrorResponse)) {
      this._errorMessage.set(ModificaDipendenteQueryService.MESSAGGI_SALVATAGGIO.GENERICO);
      return;
    }

    const body = errore.error as ErrorResponseModel | null;

    if (errore.status === 400 && body?.fieldErrors) {
      this.formService.applicaErroriBackend(form, body.fieldErrors);
      this._errorMessage.set(ModificaDipendenteQueryService.MESSAGGI_SALVATAGGIO.DATI_NON_VALIDI);
      return;
    }

    this._errorMessage.set(this.messaggioSalvataggio(errore));
  }

  /**
   * Il backend usa il 409 per due cose diverse — codice fiscale già preso e
   * dipendente non modificabile — e da fuori si distinguono solo dal testo. Non
   * potendo essere sicuri, il messaggio del duplicato lo diamo solo quando la
   * risposta lo dice; altrimenti si ripiega su quello dello stato, che è l'altro
   * caso possibile.
   */
  private messaggioSalvataggio(errore: HttpErrorResponse): string {
    const M = ModificaDipendenteQueryService.MESSAGGI_SALVATAGGIO;

    switch (errore.status) {
      case 401:
      case 403:
        return M.NON_AUTORIZZATO;
      case 404:
        return M.NON_TROVATO;
      case 409:
        return this.messaggioDelBackend(errore)?.includes('codice fiscale')
          ? M.DUPLICATO
          : M.NON_MODIFICABILE;
      default:
        return M.GENERICO;
    }
  }

  private messaggioDelBackend(errore: HttpErrorResponse): string | undefined {
    return (errore.error as ErrorResponseModel | null)?.message;
  }

  private messaggioCaricamento(errore: unknown): string {
    const M = ModificaDipendenteQueryService.MESSAGGI_CARICAMENTO;

    if (!(errore instanceof HttpErrorResponse)) {
      return M.GENERICO;
    }

    switch (errore.status) {
      case 401:
      case 403:
        return M.NON_AUTORIZZATO;
      case 404:
        return M.NON_TROVATO;
      default:
        return M.GENERICO;
    }
  }
}
