import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {Location} from '@angular/common';
import {MatButton} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {ClientiSearchBar} from './components/clienti-search-bar/clienti-search-bar';
import {ClientiTabella} from './components/clienti-tabella/clienti-tabella';
import {ClientiFiltriModel} from './interfaces/clienti-filtri.model';
import {ClientiQueryService} from './services/clienti-query.service';
import {PaginazioneModel} from '../../shared/interfaces/paginazione.model';

@Component({
  selector: 'app-clienti',
  imports: [MatButton, MatIcon, MatProgressSpinner, ClientiSearchBar, ClientiTabella],
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

  private readonly location = inject(Location);

  /** Torna alla pagina precedente nella cronologia del browser. */
  protected onIndietro(): void {
    this.location.back();
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
