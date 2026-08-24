import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { DipendentiSearchBar } from './components/dipendenti-search-bar/dipendenti-search-bar';
import { DipendentiTabella } from './components/dipendenti-tabella/dipendenti-tabella';
import { DipendenteModel } from './interfaces/dipendente.model';
import { DipendentiFiltriModel } from './interfaces/dipendenti-filtri.model';
import { DipendentiQueryService } from './services/dipendenti-query.service';
import { RinnovoDialogService } from './services/rinnovo-dialog.service';
import {
  CONFERMA_ELIMINAZIONE_DIPENDENTE,
  CONFERMA_RIPRISTINO_DIPENDENTE,
} from './constants/messaggi-dipendenti.constant';
import { PaginazioneModel } from '../../shared/interfaces/paginazione.model';
import { AppRoute } from '../../shared/enums/app-route.enum';
import { GoBack } from '../../shared/components/go-back/go-back';
import { ConfermaService } from '../../shared/services/conferma.service';

@Component({
  selector: 'app-dipendenti',
  imports: [MatButton, MatIcon, MatProgressSpinner, GoBack, DipendentiSearchBar, DipendentiTabella],
  templateUrl: './dipendenti.html',
  styleUrl: './dipendenti.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Fornito qui e non in root: lo stato della ricerca vive quanto la pagina.
  providers: [DipendentiQueryService],
})
export class Dipendenti {
  /** Stato e ricerche stanno tutti nel service: qui restano solo template ed eventi. */
  protected readonly query = inject(DipendentiQueryService);

  /** Esposto al template per il pulsante "Indietro". */
  protected readonly AppRoute = AppRoute;

  private readonly router = inject(Router);
  private readonly conferma = inject(ConfermaService);
  private readonly rinnovoDialog = inject(RinnovoDialogService);

  /** Apre la pagina di creazione. */
  protected onNuovoDipendente(): void {
    void this.router.navigate([AppRoute.NUOVO_DIPENDENTE]);
  }

  protected onFiltri(filtri: DipendentiFiltriModel): void {
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
  protected async onElimina(dipendente: DipendenteModel): Promise<void> {
    const nominativo = `${dipendente.cognome} ${dipendente.nome}`;

    const confermato = await this.conferma.chiedi({
      titolo: CONFERMA_ELIMINAZIONE_DIPENDENTE.TITOLO(nominativo),
      messaggio: CONFERMA_ELIMINAZIONE_DIPENDENTE.MESSAGGIO,
      conferma: CONFERMA_ELIMINAZIONE_DIPENDENTE.CONFERMA,
    });

    if (!confermato) {
      return;
    }

    await this.query.elimina(dipendente);
  }

  /**
   * Anche il ripristino passa da una conferma: rimette una persona negli elenchi e
   * la rende di nuovo usabile, non è un click da fare per sbaglio.
   */
  protected async onRipristina(dipendente: DipendenteModel): Promise<void> {
    const nominativo = `${dipendente.cognome} ${dipendente.nome}`;

    const confermato = await this.conferma.chiedi({
      titolo: CONFERMA_RIPRISTINO_DIPENDENTE.TITOLO(nominativo),
      messaggio: CONFERMA_RIPRISTINO_DIPENDENTE.MESSAGGIO,
      conferma: CONFERMA_RIPRISTINO_DIPENDENTE.CONFERMA,
    });

    if (!confermato) {
      return;
    }

    await this.query.ripristina(dipendente);
  }

  /**
   * Il rinnovo non chiede conferma ma un dato: la finestra domanda la nuova
   * scadenza e torna vuota se l'utente si tira indietro. Chiedere prima "sei
   * sicuro?" e poi la data sarebbero due finestre per una cosa sola.
   */
  protected async onRinnova(dipendente: DipendenteModel): Promise<void> {
    const dataScadenza = await this.rinnovoDialog.chiedi(dipendente);

    if (!dataScadenza) {
      return;
    }

    await this.query.rinnova(dipendente, dataScadenza);
  }

  /** L'interruttore "Mostra eliminati": si applica da solo, senza "Applica filtri". */
  protected onIncludiEliminati(includiEliminati: boolean): void {
    this.query.impostaIncludiEliminati(includiEliminati);
  }
}
