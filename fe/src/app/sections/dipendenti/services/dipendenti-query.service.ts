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
import { ScadenzeModel } from '../interfaces/scadenze.model';
import { PaginaResponseModel } from '../../../shared/interfaces/pagina-response.model';
import {
  PAGINAZIONE_INIZIALE,
  PaginazioneModel,
} from '../../../shared/interfaces/paginazione.model';

/** Attesa prima di interrogare il backend, così non parte una chiamata per ogni tasto. */
const DEBOUNCE_MS = 300;

/**
 * Testi degli errori delle tre operazioni di riga: li scrive e li legge solo questo
 * service. Sono separati perché la stessa risposta HTTP vuol dire cose diverse — un
 * 409 sull'eliminazione non esiste più (la cancellazione è logica e non ha vincoli
 * che la blocchino), mentre sul ripristino significa "non era eliminato".
 */
const MESSAGGI_ELIMINAZIONE = {
  NON_AUTORIZZATO: 'Non hai i permessi per eliminare un dipendente',
  NON_TROVATO: 'Il dipendente non esiste più',
  GENERICO: 'Eliminazione non riuscita, riprova',
} as const;

const MESSAGGI_RIPRISTINO = {
  NON_AUTORIZZATO: 'Non hai i permessi per ripristinare un dipendente',
  NON_TROVATO: 'Il dipendente non esiste più',
  NON_ELIMINATO: 'Il dipendente non risulta eliminato: ricarica la pagina',
  GENERICO: 'Ripristino non riuscito, riprova',
} as const;

const MESSAGGI_RINNOVO = {
  NON_AUTORIZZATO: 'Non hai i permessi per rinnovare un contratto',
  NON_TROVATO: 'Il dipendente non esiste più',
  NON_RINNOVABILE:
    'Il contratto non può essere rinnovato: il dipendente è eliminato oppure è a tempo indeterminato',
  DATA_NON_VALIDA: 'La nuova scadenza non può essere nel passato',
  GENERICO: 'Rinnovo non riuscito, riprova',
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
  /** Contratti in scadenza, per l'avviso in cima alla pagina. Null finché non risponde. */
  public readonly scadenze = computed(() => this._scadenze());
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
  /**
   * Un'operazione di riga in corso (eliminazione, ripristino, rinnovo): accende lo
   * spinner come le ricerche. Una sola bandiera per tutte e tre — a schermo l'attesa
   * è la stessa, e non c'è modo di lanciarne due insieme.
   */
  private readonly _inEliminazione = signal(false);
  private readonly _scadenze = signal<ScadenzeModel | null>(null);
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
    void this.caricaScadenze();
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

  /**
   * Rimette in anagrafica un eliminato. La riga resta dov'è — la ricerca corrente
   * ha il filtro "mostra eliminati" acceso, altrimenti quel dipendente non sarebbe
   * a schermo — quindi basta ricaricare senza toccare la pagina.
   */
  public async ripristina(dipendente: DipendenteModel): Promise<boolean> {
    return this.operazioneDiRiga(
      () => this.dipendentiApi.ripristina(dipendente.id),
      (errore) => this.messaggioRipristino(errore),
    );
  }

  /** Sposta in avanti la scadenza. La data arriva già in `yyyy-MM-dd` dalla finestra. */
  public async rinnova(dipendente: DipendenteModel, dataScadenza: string): Promise<boolean> {
    return this.operazioneDiRiga(
      () => this.dipendentiApi.rinnova(dipendente.id, { dataScadenza }),
      (errore) => this.messaggioRinnovo(errore),
    );
  }

  /**
   * Accende e spegne il filtro "Mostra eliminati". Non aspetta "Applica filtri":
   * aggiorna sia i campi a schermo sia i filtri applicati, così la tabella si
   * ricarica subito. Si torna alla prima pagina perché il numero di righe cambia,
   * e la pagina su cui eravamo potrebbe non esistere più nel nuovo risultato.
   */
  public impostaIncludiEliminati(includiEliminati: boolean): void {
    this._filtri.update((filtri) => ({ ...filtri, includiEliminati }));
    this.filtriApplicati.update((filtri) => ({ ...filtri, includiEliminati }));
    this.tornaAllaPrimaPagina();
  }

  /**
   * Ripristino e rinnovo hanno la stessa struttura: spegni l'errore, accendi
   * l'attesa, chiama, ricarica. Cambia solo la chiamata e come si traduce l'errore,
   * quindi arrivano da fuori invece di duplicare due volte lo stesso `try/finally`.
   */
  private async operazioneDiRiga(
    chiamata: () => Promise<unknown>,
    messaggio: (errore: unknown) => string,
  ): Promise<boolean> {
    this._inEliminazione.set(true);
    this._errorMessage.set(null);

    try {
      await chiamata();
      // La riga resta al suo posto: cambia il suo stato, non quante righe ci sono.
      // Basta rifare la stessa ricerca, senza toccare la pagina corrente.
      this.revisione.update((revisione) => revisione + 1);
      void this.caricaScadenze();
      return true;
    } catch (errore) {
      this._errorMessage.set(messaggio(errore));
      return false;
    } finally {
      this._inEliminazione.set(false);
    }
  }

  /**
   * L'avviso delle scadenze. Se la chiamata fallisce l'avviso sparisce e basta: è
   * un di più, e riempire la pagina di un errore rosso per un contatore mancato
   * darebbe più fastidio dell'informazione che si perde.
   */
  private async caricaScadenze(): Promise<void> {
    try {
      this._scadenze.set(await this.dipendentiApi.scadenze());
    } catch {
      this._scadenze.set(null);
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
      // Sempre presente, anche a false: non è un filtro che si può omettere, è una
      // scelta fra due elenchi diversi. E false è quello che il backend fa comunque
      // di suo, quindi mandarlo non cambia il risultato ma rende la richiesta leggibile.
      includiEliminati: filtri.includiEliminati,
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
    // Un eliminato può essere appena scomparso dalla finestra di preavviso.
    void this.caricaScadenze();

    // Con "mostra eliminati" acceso la riga non sparisce, cambia solo stato: la
    // pagina resta valida e non c'è motivo di tornare indietro.
    const eraUltimaDellaPagina =
      this._dipendentiTabella().length === 1 && !this.filtriApplicati().includiEliminati;

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

  private messaggioRipristino(errore: unknown): string {
    if (!(errore instanceof HttpErrorResponse)) {
      return MESSAGGI_RIPRISTINO.GENERICO;
    }

    switch (errore.status) {
      case 401:
      case 403:
        return MESSAGGI_RIPRISTINO.NON_AUTORIZZATO;
      case 404:
        return MESSAGGI_RIPRISTINO.NON_TROVATO;
      // Non era eliminato: qualcun altro l'ha già rimesso a posto, e quello che
      // vediamo a schermo è vecchio.
      case 409:
        return MESSAGGI_RIPRISTINO.NON_ELIMINATO;
      default:
        return MESSAGGI_RIPRISTINO.GENERICO;
    }
  }

  private messaggioRinnovo(errore: unknown): string {
    if (!(errore instanceof HttpErrorResponse)) {
      return MESSAGGI_RINNOVO.GENERICO;
    }

    switch (errore.status) {
      case 400:
        // Validazione del body: l'unico vincolo è che la data non sia passata.
        return MESSAGGI_RINNOVO.DATA_NON_VALIDA;
      case 401:
      case 403:
        return MESSAGGI_RINNOVO.NON_AUTORIZZATO;
      case 404:
        return MESSAGGI_RINNOVO.NON_TROVATO;
      // Eliminato, oppure contratto a tempo indeterminato: due casi diversi lato
      // backend, ma da qui la risposta è la stessa — quel contratto non si rinnova.
      case 409:
        return MESSAGGI_RINNOVO.NON_RINNOVABILE;
      default:
        return MESSAGGI_RINNOVO.GENERICO;
    }
  }

  private distinti(valori: string[]): string[] {
    return [...new Set(valori.filter((valore) => !!valore))];
  }

  private tornaAllaPrimaPagina(): void {
    this.paginazione.update((paginazione) => ({ ...paginazione, numeroPagina: 0 }));
  }
}
