import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { SitiSearchBar } from './components/siti-search-bar/siti-search-bar';
import { SitiTabella } from './components/siti-tabella/siti-tabella';
import { SitoModel } from './interfaces/sito.model';
import { SitiFiltriModel } from './interfaces/siti-filtri.model';
import { SitiQueryService } from './services/siti-query.service';
import { CONFERMA_ELIMINAZIONE_SITO } from './constants/messaggi-siti.constant';
import { PaginazioneModel } from '../../shared/interfaces/paginazione.model';
import { AppRoute } from '../../shared/enums/app-route.enum';
import { GoBack } from '../../shared/components/go-back/go-back';
import { ConfermaService } from '../../shared/services/conferma.service';

@Component({
  selector: 'app-siti',
  imports: [MatButton, MatIcon, MatProgressSpinner, GoBack, SitiSearchBar, SitiTabella],
  templateUrl: './siti.html',
  styleUrl: './siti.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Fornito qui e non in root: lo stato della ricerca vive quanto la pagina.
  providers: [SitiQueryService],
})
export class Siti {
  /** Stato e ricerche stanno tutti nel service: qui restano solo template ed eventi. */
  protected readonly query = inject(SitiQueryService);

  /** Esposto al template per il pulsante "Indietro". */
  protected readonly AppRoute = AppRoute;

  private readonly router = inject(Router);
  private readonly conferma = inject(ConfermaService);

  /** Apre la pagina di creazione. */
  protected onNuovoSito(): void {
    void this.router.navigate([AppRoute.NUOVO_SITO]);
  }

  protected onFiltri(filtri: SitiFiltriModel): void {
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

  /**
   * La conferma la chiede la pagina, non la tabella: la tabella mostra righe, e
   * non deve sapere che dietro al cestino c'è una chiamata che non si annulla.
   */
  protected async onElimina(sito: SitoModel): Promise<void> {
    const confermato = await this.conferma.chiedi({
      titolo: CONFERMA_ELIMINAZIONE_SITO.TITOLO(sito.nome),
      messaggio: CONFERMA_ELIMINAZIONE_SITO.MESSAGGIO,
      conferma: CONFERMA_ELIMINAZIONE_SITO.CONFERMA,
    });

    if (!confermato) {
      return;
    }

    await this.query.elimina(sito);
  }
}
