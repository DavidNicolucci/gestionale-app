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
import { SitoApiService } from './sito-api.service';
import { SitoModel } from '../interfaces/sito.model';
import { SitoRicercaRequestModel } from '../interfaces/sito-ricerca-request.model';
import { FILTRI_SITI_VUOTI, SitiFiltriModel } from '../interfaces/siti-filtri.model';
import { ClienteModel } from '../../clienti/interfaces/cliente.model';
import { ClientApiService } from '../../clienti/services/client-api.service';
import { PaginaResponseModel } from '../../../shared/interfaces/pagina-response.model';
import {
  PAGINAZIONE_INIZIALE,
  PaginazioneModel,
} from '../../../shared/interfaces/paginazione.model';

/** Attesa prima di interrogare il backend, così non parte una chiamata per ogni tasto. */
const DEBOUNCE_MS = 300;

/**
 * Testi degli errori dell'eliminazione. Il 409 è il caso concreto della sezione:
 * la cancellazione è fisica e il database rifiuta di togliere un sito su cui ci
 * sono ore registrate.
 */
const MESSAGGI_ELIMINAZIONE = {
  NON_AUTORIZZATO: 'Non hai i permessi per eliminare un sito',
  NON_TROVATO: 'Il sito è già stato eliminato',
  IN_USO: 'Il sito non può essere eliminato: ci sono ore registrate su questo sito',
  GENERICO: 'Eliminazione non riuscita, riprova',
} as const;

/** Risposta di ripiego quando non c'è niente da chiedere o la chiamata fallisce. */
const PAGINA_VUOTA: PaginaResponseModel<SitoModel> = {
  risultati: [],
  pagine: 0,
  numeroDiPagina: 0,
  totaleElementi: 0,
};

/**
 * Stato e logica della pagina siti: il componente legge i segnali esposti qui e
 * chiama i metodi, senza sapere che esiste un backend. Stessa struttura di
 * `DipendentiQueryService`, senza le parti che lì servono al ciclo di vita del
 * contratto: un sito o c'è o non c'è, non ha stati intermedi.
 *
 * Non è `providedIn: 'root'`: viene fornito dal componente Siti, così lo stato
 * riparte pulito a ogni ingresso nella pagina e le sottoscrizioni si chiudono
 * quando il componente viene distrutto.
 */
@Injectable()
export class SitiQueryService {
  public readonly filtri = computed(() => this._filtri());
  public readonly errorMessage = computed(() => this._errorMessage());
  public readonly sitiTabella = computed(() => this._sitiTabella());
  public readonly loadingTabella = computed(() => this._loadingTabella());
  public readonly totaleElementi = computed(() => this._totaleElementi());
  public readonly numeroDiPagina = computed(() => this.leggiNumeroDiPagina());
  public readonly righePerPagina = computed(() => this.leggiRighePerPagina());
  /** Una sola attesa a schermo, qualunque operazione sia in corso. */
  public readonly inCaricamento = computed(() => this.calcolaInCaricamento());
  /** Voci della tendina del nome: i valori dei siti trovati, senza doppioni. */
  public readonly opzioniNome = computed(() => this.calcolaOpzioniNome());
  /**
   * Clienti per la tendina del filtro. Non nasce dai risultati come le altre voci:
   * si filtra per un cliente qualsiasi, anche uno che al momento non ha nessun
   * sito, quindi serve l'anagrafica completa.
   */
  public readonly clienti = computed(() => this._clienti());
  private readonly sitiApi = inject(SitoApiService);
  private readonly clientiApi = inject(ClientApiService);
  private readonly _filtri = signal<SitiFiltriModel>(FILTRI_SITI_VUOTI);
  private readonly _siti = signal<SitoModel[]>([]);
  private readonly _clienti = signal<ClienteModel[]>([]);
  private readonly _loading = signal(false);
  private readonly _errorMessage = signal<string | null>(null);
  /** Righe della tabella: tutti i siti all'arrivo, solo i filtrati dopo "Applica filtri". */
  private readonly _sitiTabella = signal<SitoModel[]>([]);
  // Parte a true: la prima ricerca viene lanciata dal costruttore, e senza questo
  // la tabella mostrerebbe "Nessun sito trovato" per il tempo della chiamata.
  private readonly _loadingTabella = signal(true);
  /** Eliminazione in corso: accende lo spinner come le ricerche. */
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
   * all'arrivo sulla pagina la tabella mostra tutti i siti, e i filtri la
   * restringono invece di farla comparire.
   */
  private readonly filtriApplicati = signal<SitiFiltriModel>(FILTRI_SITI_VUOTI);
  /** Filtri applicati più pagina corrente: è la richiesta che alimenta la tabella. */
  private readonly richiestaTabella = computed(() => this.componiRichiestaTabella());

  constructor() {
    this.osservaTendine();
    this.osservaTabella();
    void this.caricaClienti();
  }

  public aggiornaFiltri(filtri: SitiFiltriModel): void {
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

  /** Svuota i filtri: la tabella non sparisce, torna a mostrare tutti i siti. */
  public rimuovi(): void {
    this._filtri.set(FILTRI_SITI_VUOTI);
    this.filtriApplicati.set({ ...FILTRI_SITI_VUOTI });
    this.tornaAllaPrimaPagina();
  }

  /** Cambio pagina o di righe per pagina: i filtri restano quelli già applicati. */
  public cambiaPagina(paginazione: PaginazioneModel): void {
    this.paginazione.set(paginazione);
  }

  /**
   * Elimina un sito e ricarica la tabella. La conferma è già stata data: qui si
   * esegue e basta. L'esito torna come booleano per chi volesse avvisare l'utente;
   * l'errore, se c'è, è già in `errorMessage`.
   */
  public async elimina(sito: SitoModel): Promise<boolean> {
    this._inEliminazione.set(true);
    this._errorMessage.set(null);

    try {
      await this.sitiApi.elimina(sito.id);
      this.ricaricaDopoEliminazione();
      return true;
    } catch (errore) {
      this._errorMessage.set(this.messaggioEliminazione(errore));
      return false;
    } finally {
      this._inEliminazione.set(false);
    }
  }

  /**
   * Anagrafica clienti per la tendina del filtro. Se la chiamata fallisce la
   * tendina resta vuota e si può comunque cercare per nome: è un filtro in meno,
   * non una pagina rotta, quindi non merita un errore rosso in cima.
   */
  private async caricaClienti(): Promise<void> {
    try {
      this._clienti.set(await this.clientiApi.lista());
    } catch {
      this._clienti.set([]);
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
    return this.distinti(this._siti().map((sito) => sito.nome));
  }

  /**
   * Richiesta completa della tabella: filtri applicati più pagina corrente.
   * Sta tutto qui dentro perché la tabella va ricaricata sia quando cambiano i
   * filtri sia quando si cambia pagina, e le due cose devono viaggiare insieme.
   */
  private componiRichiestaTabella(): SitoRicercaRequestModel {
    // Letta e scartata: è la dipendenza che permette di rifare la stessa ricerca
    // dopo un'eliminazione. Vedi `revisione`.
    this.revisione();

    return {
      ...this.componiRichiesta(this.filtriApplicati()),
      ...this.paginazione(),
    };
  }

  /** Ricerca continua mentre si digita: alimenta la tendina del nome. */
  private osservaTendine(): void {
    toObservable(this._filtri)
      .pipe(
        map((filtri) => this.componiRichiesta(filtri)),
        debounceTime(DEBOUNCE_MS),
        // I filtri sono un oggetto: senza confronto campo per campo ogni tasto
        // sembrerebbe una ricerca nuova, anche riscrivendo lo stesso testo.
        distinctUntilChanged(
          (prima, dopo) => prima.nome === dopo.nome && prima.clienteId === dopo.clienteId,
        ),
        tap(() => this._errorMessage.set(null)),
        // switchMap e non mergeMap: se l'utente continua a scrivere la risposta
        // vecchia viene scartata e non può sovrascrivere quella nuova. Sta anche
        // qui il caso "campi vuoti", che svuota l'elenco senza chiamare il backend.
        switchMap((richiesta) => {
          if (this.richiestaSenzaFiltri(richiesta)) {
            this._loading.set(false);
            return of<SitoModel[]>([]);
          }

          this._loading.set(true);
          // from(): il service torna una Promise, qui serve un observable per
          // restare dentro la catena (map, catchError, switchMap).
          return from(this.sitiApi.cerca(richiesta)).pipe(
            map((pagina) => pagina.risultati),
            catchError(() => {
              this._errorMessage.set('Ricerca non riuscita');
              return of<SitoModel[]>([]);
            }),
          );
        }),
        tap(() => this._loading.set(false)),
        takeUntilDestroyed(),
      )
      .subscribe((risultati) => this._siti.set(risultati));
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
          return from(this.sitiApi.cerca(richiesta)).pipe(
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
        this._sitiTabella.set(pagina.risultati);
        this._totaleElementi.set(pagina.totaleElementi);
      });
  }

  /**
   * Traduce i campi a schermo nei filtri del backend, che ne conosce due.
   * La casella di ricerca in alto e il filtro "nome" finiscono sullo stesso campo:
   * se il filtro è valorizzato è lui a vincere, essendo il più esplicito.
   * I campi vuoti non vengono inviati, così il backend non li usa per filtrare.
   */
  private componiRichiesta(filtri: SitiFiltriModel): SitoRicercaRequestModel {
    const nome = filtri.nome.trim() || filtri.termine.trim();

    return {
      nome: nome || undefined,
      clienteId: filtri.clienteId ?? undefined,
    };
  }

  private richiestaSenzaFiltri(richiesta: SitoRicercaRequestModel): boolean {
    return richiesta.nome === undefined && richiesta.clienteId === undefined;
  }

  /**
   * Se la riga eliminata era l'ultima della pagina si torna indietro di una:
   * restando dov'eravamo il backend risponderebbe con una pagina che non esiste
   * più e la tabella uscirebbe vuota pur essendoci dei siti. Il cambio pagina
   * ricarica da solo, quindi lì la revisione non serve.
   */
  private ricaricaDopoEliminazione(): void {
    // Qui la riga sparisce davvero: non c'è il caso dei dipendenti, dove con
    // "mostra eliminati" acceso il conteggio della pagina resta invariato.
    const eraUltimaDellaPagina = this._sitiTabella().length === 1;

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
      // Vincolo del database: sul sito ci sono dei timesheet. Il backend traduce
      // la violazione in 409 invece di lasciar passare un 500.
      case 409:
        return MESSAGGI_ELIMINAZIONE.IN_USO;
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
