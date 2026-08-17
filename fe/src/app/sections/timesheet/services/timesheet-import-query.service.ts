import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TimesheetImportApiService } from './timesheet-import-api.service';
import { DIMENSIONE_MAX_BYTE, ESTENSIONI_EXCEL } from '../constants/import-timesheet.constants';

/**
 * Scelta del file e caricamento: il componente gli passa il `File` preso
 * dall'input o dal drag & drop e legge i segnali, senza sapere che esiste un
 * backend. Come gli altri servizi di pagina non è `providedIn: 'root'`, così
 * rientrando nella pagina non ci si ritrova addosso il file e l'errore del
 * tentativo precedente.
 */
@Injectable()
export class TimesheetImportQueryService {
  /** Testi degli errori: li scrive e li usa solo questa classe. */
  private static readonly MESSAGGI = {
    ESTENSIONE: `Formato non valido: sono ammessi solo file ${ESTENSIONI_EXCEL.join(' e ')}`,
    VUOTO: 'Il file è vuoto',
    TROPPO_GRANDE: `Il file supera il limite di ${DIMENSIONE_MAX_BYTE / 1024 / 1024} MB`,
    NON_AUTORIZZATO: 'Non hai i permessi per importare i timesheet',
    GENERICO: 'Caricamento non riuscito, riprova',
  } as const;

  public readonly file = computed(() => this._file());
  public readonly inCaricamento = computed(() => this._inCaricamento());
  public readonly errorMessage = computed(() => this._errorMessage());
  private readonly importApi = inject(TimesheetImportApiService);
  private readonly _file = signal<File | null>(null);
  private readonly _inCaricamento = signal(false);
  private readonly _errorMessage = signal<string | null>(null);

  /**
   * File scelto o trascinato. Se non va bene non viene tenuto: si mostra l'errore
   * e si resta senza file, così il pulsante "Importa" non parte su qualcosa che
   * il backend rifiuterebbe.
   */
  public selezionaFile(file: File | null): void {
    const errore = file ? this.validaFile(file) : null;
    this._errorMessage.set(errore);
    this._file.set(errore ? null : file);
  }

  /**
   * Restituisce il messaggio di presa in carico del backend, oppure `null` se il
   * caricamento è fallito: così la pagina decide se confermare guardando il
   * valore, senza try/catch suo.
   */
  public async importa(): Promise<string | null> {
    const file = this._file();

    if (!file || this._inCaricamento()) {
      return null;
    }

    this._inCaricamento.set(true);
    this._errorMessage.set(null);

    try {
      const esito = await this.importApi.importa(file);
      // Il file è stato consegnato: si svuota, altrimenti un secondo click
      // rimanderebbe lo stesso identico import.
      this._file.set(null);
      return esito.messaggio;
    } catch (errore) {
      this._errorMessage.set(this.messaggioPerErrore(errore));
      return null;
    } finally {
      // Nel finally: il pulsante deve tornare cliccabile anche quando va male.
      this._inCaricamento.set(false);
    }
  }

  /** Estensione e dimensione: sul tipo MIME non si può contare, il drag & drop lo lascia vuoto. */
  private validaFile(file: File): string | null {
    const nome = file.name.toLowerCase();

    if (!ESTENSIONI_EXCEL.some((estensione) => nome.endsWith(estensione))) {
      return TimesheetImportQueryService.MESSAGGI.ESTENSIONE;
    }

    if (file.size === 0) {
      return TimesheetImportQueryService.MESSAGGI.VUOTO;
    }

    if (file.size > DIMENSIONE_MAX_BYTE) {
      return TimesheetImportQueryService.MESSAGGI.TROPPO_GRANDE;
    }

    return null;
  }

  private messaggioPerErrore(errore: unknown): string {
    if (!(errore instanceof HttpErrorResponse)) {
      return TimesheetImportQueryService.MESSAGGI.GENERICO;
    }

    switch (errore.status) {
      case 401:
      case 403:
        return TimesheetImportQueryService.MESSAGGI.NON_AUTORIZZATO;
      // 413 dal limite di Quarkus: stesso messaggio del controllo fatto qui,
      // per i file che passano la nostra soglia ma non la sua.
      case 413:
        return TimesheetImportQueryService.MESSAGGI.TROPPO_GRANDE;
      default:
        return TimesheetImportQueryService.MESSAGGI.GENERICO;
    }
  }
}
