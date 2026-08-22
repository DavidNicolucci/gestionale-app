import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { ClientiSearchBar } from './components/clienti-search-bar/clienti-search-bar';
import { ClientiTabella } from './components/clienti-tabella/clienti-tabella';
import { ClienteModel } from './interfaces/cliente.model';
import { ClientiFiltriModel } from './interfaces/clienti-filtri.model';
import { ClientiQueryService } from './services/clienti-query.service';
import { PaginazioneModel } from '../../shared/interfaces/paginazione.model';
import { AppRoute } from '../../shared/enums/app-route.enum';
import { GoBack } from '../../shared/components/go-back/go-back';
import { ConfermaService } from '../../shared/services/conferma.service';
import { CONFERMA_ELIMINAZIONE_CLIENTE } from './constants/messaggi-clienti.constant';

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
  private readonly conferma = inject(ConfermaService);

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

  /**
   * La conferma la chiede la pagina, non la tabella: la tabella mostra righe, e
   * non deve sapere che dietro al cestino c'è una chiamata che non si annulla.
   */
  protected async onElimina(cliente: ClienteModel): Promise<void> {
    const confermato = await this.conferma.chiedi({
      titolo: CONFERMA_ELIMINAZIONE_CLIENTE.TITOLO(cliente.ragioneSociale),
      messaggio: CONFERMA_ELIMINAZIONE_CLIENTE.MESSAGGIO,
      conferma: CONFERMA_ELIMINAZIONE_CLIENTE.CONFERMA,
    });

    if (!confermato) {
      return;
    }

    await this.query.elimina(cliente);
  }
}
