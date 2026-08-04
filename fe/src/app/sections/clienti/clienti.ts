import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {Location} from '@angular/common';
import {takeUntilDestroyed, toObservable} from '@angular/core/rxjs-interop';
import {MatButton} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {catchError, debounceTime, distinctUntilChanged, from, map, of, switchMap, tap} from 'rxjs';
import {ClientiSearchBar} from './components/clienti-search-bar/clienti-search-bar';
import {ClientiTabella} from './components/clienti-tabella/clienti-tabella';
import {ClienteModel} from './interfaces/cliente.model';
import {ClienteRicercaRequestModel} from './interfaces/cliente-ricerca-request.model';
import {ClientiFiltriModel, FILTRI_CLIENTI_VUOTI} from './interfaces/clienti-filtri.model';
import {ClientApiiService} from './services/client-apii.service';
import {PaginaResponseModel} from '../../shared/interfaces/pagina-response.model';
import {PAGINAZIONE_INIZIALE, PaginazioneModel} from '../../shared/interfaces/paginazione.model';

/** Attesa prima di interrogare il backend, così non parte una chiamata per ogni tasto. */
const DEBOUNCE_MS = 300;

/** Risposta di ripiego quando non c'è niente da chiedere o la chiamata fallisce. */
const PAGINA_VUOTA: PaginaResponseModel<ClienteModel> = {
  risultati: [],
  pagine: 0,
  numeroDiPagina: 0,
  totaleElementi: 0,
};

@Component({
  selector: 'app-clienti',
  imports: [MatButton, MatIcon, MatProgressSpinner, ClientiSearchBar, ClientiTabella],
  templateUrl: './clienti.html',
  styleUrl: './clienti.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Clienti {
  protected readonly filtri = signal<ClientiFiltriModel>(FILTRI_CLIENTI_VUOTI);
  protected readonly clienti = signal<ClienteModel[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  /** Voci delle tendine: i valori dei clienti trovati, senza doppioni. */
  protected readonly opzioniRagioneSociale = computed(() =>
    Clienti.distinti(this.clienti().map((cliente) => cliente.ragioneSociale)),
  );
  protected readonly opzioniPartitaIva = computed(() =>
    Clienti.distinti(this.clienti().map((cliente) => cliente.partitaIva)),
  );

  /** Righe della tabella: tutti i clienti all'arrivo, solo i filtrati dopo "Applica filtri". */
  protected readonly clientiTabella = signal<ClienteModel[]>([]);
  // Parte a true: la prima ricerca viene lanciata dal costruttore, e senza questo
  // la tabella mostrerebbe "Nessun cliente trovato" per il tempo della chiamata.
  protected readonly loadingTabella = signal(true);

  /** Pagina e righe scelte nel paginatore. Si parte da 0, come il backend. */
  private readonly paginazione = signal<PaginazioneModel>(PAGINAZIONE_INIZIALE);

  /** Totale trovato dai filtri: senza questo il paginatore non sa quante pagine ci sono. */
  protected readonly totaleElementi = signal(0);
  protected readonly numeroDiPagina = computed(() => this.paginazione().numeroPagina);
  protected readonly righePerPagina = computed(() => this.paginazione().righePerPagina);

  /** Una sola attesa a schermo, qualunque delle due ricerche sia in corso. */
  protected readonly inCaricamento = computed(() => this.loading() || this.loadingTabella());

  /**
   * Copia dei filtri fatta al click su "Applica filtri". Parte vuota, non null:
   * all'arrivo sulla pagina la tabella mostra tutti i clienti, e i filtri la
   * restringono invece di farla comparire.
   */
  private readonly filtriApplicati = signal<ClientiFiltriModel>(FILTRI_CLIENTI_VUOTI);

  /**
   * Richiesta completa della tabella: filtri applicati più pagina corrente.
   * Sta tutto qui dentro perché la tabella va ricaricata sia quando cambiano i
   * filtri sia quando si cambia pagina, e le due cose devono viaggiare insieme.
   * Con i filtri vuoti i campi non vengono inviati e il backend non filtra nulla.
   */
  private readonly richiestaTabella = computed<ClienteRicercaRequestModel>(() => ({
    ...Clienti.componiRichiesta(this.filtriApplicati()),
    ...this.paginazione(),
  }));

  private readonly clientiService = inject(ClientApiiService);
  private readonly location = inject(Location);

  constructor() {
    this.osservaTendine();
    this.osservaTabella();
  }

  /** Ricerca continua mentre si digita: alimenta le tendine dei campi. */
  private osservaTendine(): void {
    toObservable(this.filtri)
      .pipe(
        map((filtri) => Clienti.componiRichiesta(filtri)),
        debounceTime(DEBOUNCE_MS),
        // I filtri sono un oggetto: senza confronto campo per campo ogni tasto
        // sembrerebbe una ricerca nuova, anche riscrivendo lo stesso testo.
        distinctUntilChanged(
          (prima, dopo) =>
            prima.ragioneSociale === dopo.ragioneSociale && prima.partitaIva === dopo.partitaIva,
        ),
        tap(() => this.errorMessage.set(null)),
        // switchMap e non mergeMap: se l'utente continua a scrivere la risposta
        // vecchia viene scartata e non può sovrascrivere quella nuova. Sta anche
        // qui il caso "campi vuoti", che svuota l'elenco senza chiamare il backend.
        switchMap((richiesta) => {
          if (richiesta.ragioneSociale === undefined && richiesta.partitaIva === undefined) {
            this.loading.set(false);
            return of<ClienteModel[]>([]);
          }

          this.loading.set(true);
          // from(): il service torna una Promise, qui serve un observable per
          // restare dentro la catena (map, catchError, switchMap).
          return from(this.clientiService.cerca(richiesta)).pipe(
            map((pagina) => pagina.risultati),
            catchError(() => {
              this.errorMessage.set('Ricerca non riuscita');
              return of<ClienteModel[]>([]);
            }),
          );
        }),
        tap(() => this.loading.set(false)),
        takeUntilDestroyed(),
      )
      .subscribe((risultati) => this.clienti.set(risultati));
  }

  /**
   * Ricerca della tabella: niente debounce. Parte una prima volta da sola all'arrivo
   * sulla pagina (senza filtri), poi a ogni click su "Applica filtri" perché onApplica
   * emette sempre un oggetto nuovo, e a ogni cambio di pagina.
   */
  private osservaTabella(): void {
    toObservable(this.richiestaTabella)
      .pipe(
        tap(() => this.errorMessage.set(null)),
        switchMap((richiesta) => {
          this.loadingTabella.set(true);
          return from(this.clientiService.cerca(richiesta)).pipe(
            catchError(() => {
              this.errorMessage.set('Ricerca non riuscita');
              return of(PAGINA_VUOTA);
            }),
          );
        }),
        tap(() => this.loadingTabella.set(false)),
        takeUntilDestroyed(),
      )
      .subscribe((pagina) => {
        this.clientiTabella.set(pagina.risultati);
        this.totaleElementi.set(pagina.totaleElementi);
      });
  }

  /**
   * Traduce i campi a schermo nei filtri del backend, che ne conosce solo due.
   * La casella di ricerca in alto e il filtro "ragione sociale" finiscono sullo
   * stesso campo: se il filtro è valorizzato è lui a vincere, essendo il più esplicito.
   * I campi vuoti non vengono inviati, così il backend non li usa per filtrare.
   */
  private static componiRichiesta(filtri: ClientiFiltriModel): ClienteRicercaRequestModel {
    const ragioneSociale = filtri.ragioneSociale.trim() || filtri.termine.trim();
    const partitaIva = filtri.partitaIva.trim();

    return {
      ragioneSociale: ragioneSociale || undefined,
      partitaIva: partitaIva || undefined,
    };
  }

  private static distinti(valori: string[]): string[] {
    return [...new Set(valori.filter((valore) => !!valore))];
  }

  /** Torna alla pagina precedente nella cronologia del browser. */
  protected onIndietro(): void {
    this.location.back();
  }

  protected onFiltri(filtri: ClientiFiltriModel): void {
    this.filtri.set(filtri);
  }

  /** Copia sempre in un oggetto nuovo: così anche riapplicando gli stessi filtri la ricerca riparte. */
  protected onApplica(): void {
    // Filtri nuovi, risultato nuovo: si torna alla prima pagina. Restando sulla
    // pagina corrente se ne chiederebbe una che nel nuovo risultato può non esistere,
    // e la tabella uscirebbe vuota pur essendoci righe.
    this.paginazione.update((paginazione) => ({...paginazione, numeroPagina: 0}));
    this.filtriApplicati.set({...this.filtri()});
  }

  /** Svuota i filtri: la tabella non sparisce, torna a mostrare tutti i clienti. */
  protected onRimuovi(): void {
    this.filtri.set(FILTRI_CLIENTI_VUOTI);
    this.filtriApplicati.set({...FILTRI_CLIENTI_VUOTI});
    this.paginazione.update((paginazione) => ({...paginazione, numeroPagina: 0}));
  }

  /** Cambio pagina o di righe per pagina: i filtri restano quelli già applicati. */
  protected onPagina(paginazione: PaginazioneModel): void {
    this.paginazione.set(paginazione);
  }
}
