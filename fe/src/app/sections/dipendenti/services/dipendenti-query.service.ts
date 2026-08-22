import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  from,
  map,
  of,
  switchMap,
  tap,
} from 'rxjs';
import { DipendenteApiService } from './dipendente-api.service';
import { DipendenteModel } from '../interfaces/dipendente.model';
import { DipendenteRicercaRequestModel } from '../interfaces/dipendente-ricerca-request.model';
import {
  DipendentiFiltriModel,
  FILTRI_DIPENDENTI_VUOTI,
} from '../interfaces/dipendenti-filtri.model';
import { PaginaResponseModel } from '../../../shared/interfaces/pagina-response.model';
import {
  PAGINAZIONE_INIZIALE,
  PaginazioneModel,
} from '../../../shared/interfaces/paginazione.model';

/** Attesa prima di interrogare il backend, così non parte una chiamata per ogni tasto. */
const DEBOUNCE_MS = 300;

/**
 * Testi degli errori di eliminazione: li scrive e li legge solo questo service.
 * Il 409 qui è il caso concreto: la FK dei timesheet non cancella a cascata,
 * quindi il database rifiuta e il `ThrowableMapper` lo traduce in un conflitto.
 */
const MESSAGGI_ELIMINAZIONE = {
  NON_AUTORIZZATO: 'Non hai i permessi per eliminare un dipendente',
  NON_TROVATO: 'Il dipendente è già stato eliminato',
  CON_TIMESHEET: 'Il dipendente ha dei timesheet registrati e non può essere eliminato',
  GENERICO: 'Eliminazione non riuscita, riprova',
} as const;

/** Risposta di ripiego quando non c'è niente da chiedere o la chiamata fallisce. */
const PAGINA_VUOTA: PaginaResponseModel<DipendenteModel> = {
  risultati: [],
  pagine: 0,
  numeroDiPagina: 0,
  totaleElementi: 0,
};

/**
 * Stato e logica della pagina dipendenti: il componente legge i segnali esposti
 * qui e chiama i metodi, senza sapere che esiste un backend. Stessa struttura di
 * `ClientiQueryService`, che resta la sezione di riferimento.
 *
 * Non è `providedIn: 'root'`: viene fornito dal componente Dipendenti, così lo
 * stato riparte pulito a ogni ingresso nella pagina e le sottoscrizioni si
 * chiudono quando il componente viene distrutto.
 */
@Injectable()
export class DipendentiQueryService {
  public readonly filtri = computed(() => this._filtri());
  public readonly errorMessage = computed(() => this._errorMessage());
  public readonly dipendentiTabella = computed(() => this._dipendentiTabella());
  public readonly loadingTabella = computed(() => this._loadingTabella());
  public readonly totaleElementi = computed(() => this._totaleElementi());
  public readonly numeroDiPagina = computed(() => this.leggiNumeroDiPagina());
  public readonly righePerPagina = computed(() => this.leggiRighePerPagina());
  /** Una sola attesa a schermo, qualunque operazione sia in corso. */
  public readonly inCaricamento = computed(() => this.calcolaInCaricamento());
  /** Voci delle tendine: i valori dei dipendenti trovati, senza doppioni. */
  public readonly opzioniNome = computed(() => this.calcolaOpzioniNome());
  public readonly opzioniCognome = computed(() => this.calcolaOpzioniCognome());
  public readonly opzioniCodiceFiscale = computed(() => this.calcolaOpzioniCodiceFiscale());
  private readonly dipendentiApi = inject(DipendenteApiService);
  private readonly _filtri = signal<DipendentiFiltriModel>(FILTRI_DIPENDENTI_VUOTI);
  private readonly _dipendenti = signal<DipendenteModel[]>([]);
  private readonly _loading = signal(false);
  private readonly _errorMessage = signal<string | null>(null);
  /** Righe della tabella: tutti i dipendenti all'arrivo, solo i filtrati dopo "Applica filtri". */
  private readonly _dipendentiTabella = signal<DipendenteModel[]>([]);
  // Parte a true: la prima ricerca viene lanciata dal costruttore, e senza questo
  // la tabella mostrerebbe "Nessun dipendente trovato" per il tempo della chiamata.
  private readonly _loadingTabella = signal(true);
  /** Un'eliminazione in corso: accende lo spinner come le ricerche. */
  private readonly _inEliminazione = signal(false);
  /** Totale trovato dai filtri: senza questo il paginatore non sa quante pagine ci sono. */
  private readonly _totaleElementi = signal(0);
  /** Pagina e righe scelte nel paginatore. Si parte da 0, come il backend. */
  private readonly paginazione = signal<PaginazioneModel>(PAGINAZIONE_INIZIALE);
  /**
   * Non finisce nella richiesta: serve solo a far ripartire la ricerca della
   * tabella a parità di filtri e pagina. Dopo un'eliminazione le righe sono
   * cambiate ma la domanda da fare al backend è identica, e senza questo
   * `richiestaTabella` non ricalcolerebbe.
   */
  private readonly revisione = signal(0);
  /**
   * Copia dei filtri fatta al click su "Applica filtri". Parte vuota, non null:
   * all'arrivo sulla pagina la tabella mostra tutti i dipendenti, e i filtri la
   * restringono invece di farla comparire.
   */
  private readonly filtriApplicati = signal<DipendentiFiltriModel>(FILTRI_DIPENDENTI_VUOTI);
  /** Filtri applicati più pagina corrente: è la richiesta che alimenta la tabella. */
  private readonly richiestaTabella = computed(() => this.componiRichiestaTabella());

  constructor() {
    this.osservaTendine();
    this.osservaTabella();
  }

  public aggiornaFiltri(filtri: DipendentiFiltriModel): void {
    this._filtri.set(filtri);
  }

  /** Copia sempre in un oggetto nuovo: così anche riapplicando gli stessi filtri la ricerca riparte. */
  public applica(): void {
    // Filtri nuovi, risultato nuovo: si torna alla prima pagina. Restando sulla
    // pagina corrente se ne chiederebbe una che nel nuovo risultato può non
    // esistere, e la tabella uscirebbe vuota pur essendoci righe.
    this.tornaAllaPrimaPagina();
    this.filtriApplicati.set({ ...this._filtri() });
  }

  /** Svuota i filtri: la tabella non sparisce, torna a mostrare tutti i dipendenti. */
  public rimuovi(): void {
    this._filtri.set(FILTRI_DIPENDENTI_VUOTI);
    this.filtriApplicati.set({ ...FILTRI_DIPENDENTI_VUOTI });
    this.tornaAllaPrimaPagina();
  }

  /** Cambio pagina o di righe per pagina: i filtri restano quelli già applicati. */
  public cambiaPagina(paginazione: PaginazioneModel): void {
    this.paginazione.set(paginazione);
  }

  /**
   * Elimina un dipendente e ricarica la tabella. La conferma è già stata data:
   * qui si esegue e basta. L'esito torna come booleano per chi volesse avvisare
   * l'utente; l'errore, se c'è, è già in `errorMessage`.
   */
  public async elimina(dipendente: DipendenteModel): Promise<boolean> {
    this._inEliminazione.set(true);
    this._errorMessage.set(null);

    try {
      await this.dipendentiApi.elimina(dipendente.id);
      this.ricaricaDopoEliminazione();
      return true;
    } catch (errore) {
      this._errorMessage.set(this.messaggioEliminazione(errore));
      return false;
    } finally {
      this._inEliminazione.set(false);
    }
  }

  private leggiNumeroDiPagina(): number {
    return this.paginazione().numeroPagina;
  }

  private leggiRighePerPagina(): number {
    return this.paginazione().righePerPagina;
  }

  private calcolaInCaricamento(): boolean {
    return this._loading() || this._loadingTabella() || this._inEliminazione();
  }

  private calcolaOpzioniNome(): string[] {
    return this.distinti(this._dipendenti().map((dipendente) => dipendente.nome));
  }

  private calcolaOpzioniCognome(): string[] {
    return this.distinti(this._dipendenti().map((dipendente) => dipendente.cognome));
  }

  private calcolaOpzioniCodiceFiscale(): string[] {
    return this.distinti(this._dipendenti().map((dipendente) => dipendente.codiceFiscale));
  }

  /**
   * Richiesta completa della tabella: filtri applicati più pagina corrente.
   * Sta tutto qui dentro perché la tabella va ricaricata sia quando cambiano i
   * filtri sia quando si cambia pagina, e le due cose devono viaggiare insieme.
   */
  private componiRichiestaTabella(): DipendenteRicercaRequestModel {
    // Letta e scartata: è la dipendenza che permette di rifare la stessa ricerca
    // dopo un'eliminazione. Vedi `revisione`.
    this.revisione();

    return {
      ...this.componiRichiesta(this.filtriApplicati()),
      ...this.paginazione(),
    };
  }

  /** Ricerca continua mentre si digita: alimenta le tendine dei campi. */
  private osservaTendine(): void {
    toObservable(this._filtri)
      .pipe(
        map((filtri) => this.componiRichiesta(filtri)),
        debounceTime(DEBOUNCE_MS),
        // I filtri sono un oggetto: senza confronto campo per campo ogni tasto
        // sembrerebbe una ricerca nuova, anche riscrivendo lo stesso testo.
        distinctUntilChanged(
          (prima, dopo) =>
            prima.nome === dopo.nome &&
            prima.cognome === dopo.cognome &&
            prima.codiceFiscale === dopo.codiceFiscale,
        ),
        tap(() => this._errorMessage.set(null)),
        // switchMap e non mergeMap: se l'utente continua a scrivere la risposta
        // vecchia viene scartata e non può sovrascrivere quella nuova. Sta anche
        // qui il caso "campi vuoti", che svuota l'elenco senza chiamare il backend.
        switchMap((richiesta) => {
          if (this.richiestaSenzaFiltri(richiesta)) {
            this._loading.set(false);
            return of<DipendenteModel[]>([]);
          }

          this._loading.set(true);
          // from(): il service torna una Promise, qui serve un observable per
          // restare dentro la catena (map, catchError, switchMap).
          return from(this.dipendentiApi.cerca(richiesta)).pipe(
            map((pagina) => pagina.risultati),
            catchError(() => {
              this._errorMessage.set('Ricerca non riuscita');
              return of<DipendenteModel[]>([]);
            }),
          );
        }),
        tap(() => this._loading.set(false)),
        takeUntilDestroyed(),
      )
      .subscribe((risultati) => this._dipendenti.set(risultati));
  }

  /**
   * Ricerca della tabella. Parte una prima volta da sola all'arrivo sulla pagina
   * (senza filtri), poi a ogni click su "Applica filtri" perché applica() emette
   * sempre un oggetto nuovo, a ogni cambio di pagina e dopo un'eliminazione.
   */
  private osservaTabella(): void {
    toObservable(this.richiestaTabella)
      .pipe(
        tap(() => this._errorMessage.set(null)),
        switchMap((richiesta) => {
          this._loadingTabella.set(true);
          return from(this.dipendentiApi.cerca(richiesta)).pipe(
            catchError(() => {
              this._errorMessage.set('Ricerca non riuscita');
              return of(PAGINA_VUOTA);
            }),
          );
        }),
        tap(() => this._loadingTabella.set(false)),
        takeUntilDestroyed(),
      )
      .subscribe((pagina) => {
        this._dipendentiTabella.set(pagina.risultati);
        this._totaleElementi.set(pagina.totaleElementi);
      });
  }

  /**
   * Traduce i campi a schermo nei filtri del backend, che ne conosce tre.
   * La casella di ricerca in alto e il filtro "cognome" finiscono sullo stesso
   * campo: se il filtro è valorizzato è lui a vincere, essendo il più esplicito.
   * I campi vuoti non vengono inviati, così il backend non li usa per filtrare.
   */
  private componiRichiesta(filtri: DipendentiFiltriModel): DipendenteRicercaRequestModel {
    const cognome = filtri.cognome.trim() || filtri.termine.trim();
    const nome = filtri.nome.trim();
    const codiceFiscale = filtri.codiceFiscale.trim();

    return {
      nome: nome || undefined,
      cognome: cognome || undefined,
      codiceFiscale: codiceFiscale || undefined,
    };
  }

  private richiestaSenzaFiltri(richiesta: DipendenteRicercaRequestModel): boolean {
    return (
      richiesta.nome === undefined &&
      richiesta.cognome === undefined &&
      richiesta.codiceFiscale === undefined
    );
  }

  /**
   * Se la riga eliminata era l'ultima della pagina si torna indietro di una:
   * restando dov'eravamo il backend risponderebbe con una pagina che non esiste
   * più e la tabella uscirebbe vuota pur essendoci dipendenti. Il cambio pagina
   * ricarica da solo, quindi lì la revisione non serve.
   */
  private ricaricaDopoEliminazione(): void {
    const eraUltimaDellaPagina = this._dipendentiTabella().length === 1;

    if (eraUltimaDellaPagina && this.leggiNumeroDiPagina() > 0) {
      this.paginazione.update((paginazione) => ({
        ...paginazione,
        numeroPagina: paginazione.numeroPagina - 1,
      }));
      return;
    }

    this.revisione.update((revisione) => revisione + 1);
  }

  private messaggioEliminazione(errore: unknown): string {
    if (!(errore instanceof HttpErrorResponse)) {
      return MESSAGGI_ELIMINAZIONE.GENERICO;
    }

    switch (errore.status) {
      case 401:
      case 403:
        return MESSAGGI_ELIMINAZIONE.NON_AUTORIZZATO;
      case 404:
        return MESSAGGI_ELIMINAZIONE.NON_TROVATO;
      case 409:
        return MESSAGGI_ELIMINAZIONE.CON_TIMESHEET;
      default:
        return MESSAGGI_ELIMINAZIONE.GENERICO;
    }
  }

  private distinti(valori: string[]): string[] {
    return [...new Set(valori.filter((valore) => !!valore))];
  }

  private tornaAllaPrimaPagina(): void {
    this.paginazione.update((paginazione) => ({ ...paginazione, numeroPagina: 0 }));
  }
}
