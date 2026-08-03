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

/** Attesa prima di interrogare il backend, così non parte una chiamata per ogni tasto. */
const DEBOUNCE_MS = 300;

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

  /** Righe della tabella: si aggiornano solo con "Applica filtri". */
  protected readonly clientiTabella = signal<ClienteModel[]>([]);
  protected readonly loadingTabella = signal(false);
  protected readonly tabellaVisibile = signal(false);

  /** Una sola attesa a schermo, qualunque delle due ricerche sia in corso. */
  protected readonly inCaricamento = computed(() => this.loading() || this.loadingTabella());

  /** Copia dei filtri fatta al click su "Applica filtri". */
  private readonly filtriApplicati = signal<ClientiFiltriModel | null>(null);

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
   * Ricerca esplicita per la tabella: niente debounce, parte a ogni click su
   * "Applica filtri" perché onApplica emette sempre un oggetto nuovo.
   */
  private osservaTabella(): void {
    toObservable(this.filtriApplicati)
      .pipe(
        tap(() => this.errorMessage.set(null)),
        switchMap((filtri) => {
          if (filtri === null) {
            this.loadingTabella.set(false);
            return of<ClienteModel[]>([]);
          }

          this.loadingTabella.set(true);
          return from(this.clientiService.cerca(Clienti.componiRichiesta(filtri))).pipe(
            map((pagina) => pagina.risultati),
            catchError(() => {
              this.errorMessage.set('Ricerca non riuscita');
              return of<ClienteModel[]>([]);
            }),
          );
        }),
        tap(() => this.loadingTabella.set(false)),
        takeUntilDestroyed(),
      )
      .subscribe((risultati) => this.clientiTabella.set(risultati));
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
    this.tabellaVisibile.set(true);
    this.filtriApplicati.set({...this.filtri()});
  }

  protected onRimuovi(): void {
    this.tabellaVisibile.set(false);
    this.filtriApplicati.set(null);
    this.filtri.set(FILTRI_CLIENTI_VUOTI);
  }
}
