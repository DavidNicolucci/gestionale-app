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
import { ClientApiService } from './client-api.service';
import { ClienteModel } from '../interfaces/cliente.model';
import { ClienteRicercaRequestModel } from '../interfaces/cliente-ricerca-request.model';
import { ClientiFiltriModel, FILTRI_CLIENTI_VUOTI } from '../interfaces/clienti-filtri.model';
import { PaginaResponseModel } from '../../../shared/interfaces/pagina-response.model';
import {
  PAGINAZIONE_INIZIALE,
  PaginazioneModel,
} from '../../../shared/interfaces/paginazione.model';

/** Attesa prima di interrogare il backend, così non parte una chiamata per ogni tasto. */
const DEBOUNCE_MS = 300;

/** Testi degli errori di eliminazione: li scrive e li legge solo questo service. */
const MESSAGGI_ELIMINAZIONE = {
  NON_AUTORIZZATO: 'Non hai i permessi per eliminare un cliente',
  NON_TROVATO: 'Il cliente è già stato eliminato',
  GENERICO: 'Eliminazione non riuscita, riprova',
} as const;

/** Risposta di ripiego quando non c'è niente da chiedere o la chiamata fallisce. */
const PAGINA_VUOTA: PaginaResponseModel<ClienteModel> = {
  risultati: [],
  pagine: 0,
  numeroDiPagina: 0,
  totaleElementi: 0,
};

/**
 * Stato e logica della pagina clienti: il componente legge i segnali esposti qui
 * e chiama i metodi, senza sapere che esiste un backend.
 *
 * Non è `providedIn: 'root'`: viene fornito dal componente Clienti, così lo stato
 * (filtri, pagina corrente) riparte pulito a ogni ingresso nella pagina e le
 * sottoscrizioni si chiudono quando il componente viene distrutto.
 *
 * Le proprietà pubbliche sono solo `computed` di una riga che rimandano a un
 * metodo: il calcolo sta nei metodi qui sotto, non nella dichiarazione. Sono
 * `computed` e non `asReadonly()` perché il corpo viene eseguito alla prima
 * lettura, e non al momento della dichiarazione: così i campi pubblici possono
 * stare sopra ai segnali privati che leggono.
 */
@Injectable()
export class ClientiQueryService {
  public readonly filtri = computed(() => this._filtri());
  public readonly errorMessage = computed(() => this._errorMessage());
  public readonly clientiTabella = computed(() => this._clientiTabella());
  public readonly loadingTabella = computed(() => this._loadingTabella());
  public readonly totaleElementi = computed(() => this._totaleElementi());
  public readonly numeroDiPagina = computed(() => this.leggiNumeroDiPagina());
  public readonly righePerPagina = computed(() => this.leggiRighePerPagina());
  /** Una sola attesa a schermo, qualunque delle due ricerche sia in corso. */
  public readonly inCaricamento = computed(() => this.calcolaInCaricamento());
  /** Voci delle tendine: i valori dei clienti trovati, senza doppioni. */
  public readonly opzioniRagioneSociale = computed(() => this.calcolaOpzioniRagioneSociale());
  public readonly opzioniPartitaIva = computed(() => this.calcolaOpzioniPartitaIva());
  private readonly clientiApi = inject(ClientApiService);
  private readonly _filtri = signal<ClientiFiltriModel>(FILTRI_CLIENTI_VUOTI);
  private readonly _clienti = signal<ClienteModel[]>([]);
  private readonly _loading = signal(false);
  private readonly _errorMessage = signal<string | null>(null);
  /** Righe della tabella: tutti i clienti all'arrivo, solo i filtrati dopo "Applica filtri". */
  private readonly _clientiTabella = signal<ClienteModel[]>([]);
  // Parte a true: la prima ricerca viene lanciata dal costruttore, e senza questo
  // la tabella mostrerebbe "Nessun cliente trovato" per il tempo della chiamata.
  private readonly _loadingTabella = signal(true);
  /** Un'eliminazione in corso: accende lo spinner come le ricerche. */
  private readonly _inEliminazione = signal(false);
  /**
   * Non finisce nella richiesta: serve solo a far ripartire la ricerca della
   * tabella a parità di filtri e pagina. Dopo un'eliminazione le righe sono
   * cambiate ma la domanda da fare al backend è identica, e senza questo
   * `richiestaTabella` non ricalcolerebbe.
   */
  private readonly revisione = signal(0);
  /** Totale trovato dai filtri: senza questo il paginatore non sa quante pagine ci sono. */
  private readonly _totaleElementi = signal(0);
  /** Pagina e righe scelte nel paginatore. Si parte da 0, come il backend. */
  private readonly paginazione = signal<PaginazioneModel>(PAGINAZIONE_INIZIALE);
  /**
   * Copia dei filtri fatta al click su "Applica filtri". Parte vuota, non null:
   * all'arrivo sulla pagina la tabella mostra tutti i clienti, e i filtri la
   * restringono invece di farla comparire.
   */
  private readonly filtriApplicati = signal<ClientiFiltriModel>(FILTRI_CLIENTI_VUOTI);
  /** Filtri applicati più pagina corrente: è la richiesta che alimenta la tabella. */
  private readonly richiestaTabella = computed(() => this.componiRichiestaTabella());

  constructor() {
    this.osservaTendine();
    this.osservaTabella();
  }

  public aggiornaFiltri(filtri: ClientiFiltriModel): void {
    this._filtri.set(filtri);
  }

  /** Copia sempre in un oggetto nuovo: così anche riapplicando gli stessi filtri la ricerca riparte. */
  public applica(): void {
    // Filtri nuovi, risultato nuovo: si torna alla prima pagina. Restando sulla
    // pagina corrente se ne chiederebbe una che nel nuovo risultato può non esistere,
    // e la tabella uscirebbe vuota pur essendoci righe.
    this.tornaAllaPrimaPagina();
    this.filtriApplicati.set({ ...this._filtri() });
  }

  /** Svuota i filtri: la tabella non sparisce, torna a mostrare tutti i clienti. */
  public rimuovi(): void {
    this._filtri.set(FILTRI_CLIENTI_VUOTI);
    this.filtriApplicati.set({ ...FILTRI_CLIENTI_VUOTI });
    this.tornaAllaPrimaPagina();
  }

  /** Cambio pagina o di righe per pagina: i filtri restano quelli già applicati. */
  public cambiaPagina(paginazione: PaginazioneModel): void {
    this.paginazione.set(paginazione);
  }

  /**
   * Elimina un cliente e ricarica la tabella. La conferma è già stata data: qui
   * si esegue e basta. L'esito torna come booleano per chi volesse avvisare
   * l'utente; l'errore, se c'è, è già in `errorMessage`.
   */
  public async elimina(cliente: ClienteModel): Promise<boolean> {
    this._inEliminazione.set(true);
    this._errorMessage.set(null);

    try {
      await this.clientiApi.elimina(cliente.id);
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

  private calcolaOpzioniRagioneSociale(): string[] {
    return this.distinti(this._clienti().map((cliente) => cliente.ragioneSociale));
  }

  private calcolaOpzioniPartitaIva(): string[] {
    return this.distinti(this._clienti().map((cliente) => cliente.partitaIva));
  }

  /**
   * Richiesta completa della tabella: filtri applicati più pagina corrente.
   * Sta tutto qui dentro perché la tabella va ricaricata sia quando cambiano i
   * filtri sia quando si cambia pagina, e le due cose devono viaggiare insieme.
   * Con i filtri vuoti i campi non vengono inviati e il backend non filtra nulla.
   */
  private componiRichiestaTabella(): ClienteRicercaRequestModel {
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
            prima.ragioneSociale === dopo.ragioneSociale && prima.partitaIva === dopo.partitaIva,
        ),
        tap(() => this._errorMessage.set(null)),
        // switchMap e non mergeMap: se l'utente continua a scrivere la risposta
        // vecchia viene scartata e non può sovrascrivere quella nuova. Sta anche
        // qui il caso "campi vuoti", che svuota l'elenco senza chiamare il backend.
        switchMap((richiesta) => {
          if (richiesta.ragioneSociale === undefined && richiesta.partitaIva === undefined) {
            this._loading.set(false);
            return of<ClienteModel[]>([]);
          }

          this._loading.set(true);
          // from(): il service torna una Promise, qui serve un observable per
          // restare dentro la catena (map, catchError, switchMap).
          return from(this.clientiApi.cerca(richiesta)).pipe(
            map((pagina) => pagina.risultati),
            catchError(() => {
              this._errorMessage.set('Ricerca non riuscita');
              return of<ClienteModel[]>([]);
            }),
          );
        }),
        tap(() => this._loading.set(false)),
        takeUntilDestroyed(),
      )
      .subscribe((risultati) => this._clienti.set(risultati));
  }

  /**
   * Ricerca della tabella. Parte una prima volta da sola all'arrivo sulla pagina
   * (senza filtri), poi a ogni click su "Applica filtri" perché applica() emette
   * sempre un oggetto nuovo, e a ogni cambio di pagina.
   */
  private osservaTabella(): void {
    toObservable(this.richiestaTabella)
      .pipe(
        tap(() => this._errorMessage.set(null)),
        switchMap((richiesta) => {
          this._loadingTabella.set(true);
          return from(this.clientiApi.cerca(richiesta)).pipe(
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
        this._clientiTabella.set(pagina.risultati);
        this._totaleElementi.set(pagina.totaleElementi);
      });
  }

  /**
   * Traduce i campi a schermo nei filtri del backend, che ne conosce solo due.
   * La casella di ricerca in alto e il filtro "ragione sociale" finiscono sullo
   * stesso campo: se il filtro è valorizzato è lui a vincere, essendo il più esplicito.
   * I campi vuoti non vengono inviati, così il backend non li usa per filtrare.
   */
  private componiRichiesta(filtri: ClientiFiltriModel): ClienteRicercaRequestModel {
    const ragioneSociale = filtri.ragioneSociale.trim() || filtri.termine.trim();
    const partitaIva = filtri.partitaIva.trim();

    return {
      ragioneSociale: ragioneSociale || undefined,
      partitaIva: partitaIva || undefined,
    };
  }

  /**
   * Se la riga eliminata era l'ultima della pagina si torna indietro di una:
   * restando dov'eravamo il backend risponderebbe con una pagina che non esiste
   * più e la tabella uscirebbe vuota pur essendoci clienti. Il cambio pagina
   * ricarica da solo, quindi lì la revisione non serve.
   */
  private ricaricaDopoEliminazione(): void {
    const eraUltimaDellaPagina = this._clientiTabella().length === 1;

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
