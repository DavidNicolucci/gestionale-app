import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {Location} from '@angular/common';
import {takeUntilDestroyed, toObservable} from '@angular/core/rxjs-interop';
import {MatButton} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';
import {catchError, debounceTime, distinctUntilChanged, from, map, of, switchMap, tap} from 'rxjs';
import {ClientiSearchBar} from './components/clienti-search-bar/clienti-search-bar';
import {ClienteModel} from './interfaces/cliente.model';
import {ClientiService} from './services/clienti.service';

/** Attesa prima di interrogare il backend, così non parte una chiamata per ogni tasto. */
const DEBOUNCE_MS = 300;

@Component({
  selector: 'app-clienti',
  imports: [MatButton, MatIcon, ClientiSearchBar],
  templateUrl: './clienti.html',
  styleUrl: './clienti.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Clienti {
  protected readonly termine = signal('');
  protected readonly clienti = signal<ClienteModel[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly haRicerca = computed(() => this.termine().trim().length > 0);

  private readonly clientiService = inject(ClientiService);
  private readonly location = inject(Location);

  constructor() {
    toObservable(this.termine)
      .pipe(
        map((valore) => valore.trim()),
        debounceTime(DEBOUNCE_MS),
        distinctUntilChanged(),
        tap(() => this.errorMessage.set(null)),
        // switchMap e non mergeMap: se l'utente continua a scrivere la risposta
        // vecchia viene scartata e non può sovrascrivere quella nuova. Sta anche
        // qui il caso "casella vuota": così annulla la chiamata ancora in corso.
        switchMap((termine) => {
          if (!termine) {
            this.loading.set(false);
            return of<ClienteModel[]>([]);
          }

          this.loading.set(true);
          // from(): il service torna una Promise, qui serve un observable per
          // restare dentro la catena (map, catchError, switchMap).
          return from(this.clientiService.cerca({ragioneSociale: termine})).pipe(
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

  /** Torna alla pagina precedente nella cronologia del browser. */
  protected onIndietro(): void {
    this.location.back();
  }

  protected onRicerca(termine: string): void {
    this.termine.set(termine);
  }
}
