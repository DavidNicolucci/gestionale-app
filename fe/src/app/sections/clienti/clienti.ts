import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {Router} from '@angular/router';
import {MatButton} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {ClientiSearchBar} from './components/clienti-search-bar/clienti-search-bar';
import {ClientiTabella} from './components/clienti-tabella/clienti-tabella';
import {ClientiFiltriModel} from './interfaces/clienti-filtri.model';
import {ClientiQueryService} from './services/clienti-query.service';
import {PaginazioneModel} from '../../shared/interfaces/paginazione.model';
import {AppRoute} from '../../shared/enums/app-route.enum';
import {GoBack} from '../../shared/components/go-back/go-back';

@Component({
  selector: 'app-clienti',
  imports: [MatButton, MatIcon, MatProgressSpinner, GoBack, ClientiSearchBar, ClientiTabella],
  templateUrl: './clienti.html',
  styleUrl: './clienti.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Fornito qui e non in root: lo stato della ricerca vive quanto la pagina.
  providers: [ClientiQueryService],
})
export class Clienti {
  /** Stato e ricerche stanno tutti nel service: qui restano solo template ed eventi. */
  protected readonly query = inject(ClientiQueryService);

  /** Esposto al template per il pulsante "Indietro". */
  protected readonly AppRoute = AppRoute;

  private readonly router = inject(Router);

  /** Apre la pagina di creazione. */
  protected onNuovoCliente(): void {
    void this.router.navigate([AppRoute.NUOVO_CLIENTE]);
  }

  protected onFiltri(filtri: ClientiFiltriModel): void {
    this.query.aggiornaFiltri(filtri);
  }

  protected onApplica(): void {
    this.query.applica();
  }

  protected onRimuovi(): void {
    this.query.rimuovi();
  }

  protected onPagina(paginazione: PaginazioneModel): void {
    this.query.cambiaPagina(paginazione);
  }
}
