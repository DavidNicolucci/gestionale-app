import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { DipendenteModel } from '../../interfaces/dipendente.model';
import { ETICHETTE_TIPO_CONTRATTO, TipoContratto } from '../../enums/tipo-contratto.enum';
import {
  OPZIONI_RIGHE_PER_PAGINA,
  PaginazioneModel,
  RIGHE_PER_PAGINA_DEFAULT,
} from '../../../../shared/interfaces/paginazione.model';
import { paginatoreItaliano } from '../../../../shared/services/paginatore-italiano';

/** Mostrato al posto di una data che non c'è: un contratto indeterminato non scade. */
const SENZA_SCADENZA = 'Nessuna';

/** Tabella dei dipendenti trovati: riceve le righe già pronte dal componente pagina. */
@Component({
  selector: 'app-dipendenti-tabella',
  imports: [DatePipe, MatIcon, MatIconButton, MatTableModule, MatPaginatorModule],
  templateUrl: './dipendenti-tabella.html',
  styleUrl: './dipendenti-tabella.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: MatPaginatorIntl, useFactory: paginatoreItaliano }],
})
export class DipendentiTabella {
  readonly dipendenti = input.required<DipendenteModel[]>();
  readonly loading = input(false);

  /** Righe totali trovate dai filtri, non solo quelle di questa pagina. */
  readonly totaleElementi = input(0);
  readonly numeroDiPagina = input(0);
  readonly righePerPagina = input(RIGHE_PER_PAGINA_DEFAULT);

  /** La pagina la sceglie l'utente qui, ma a chiederla al backend è il componente pagina. */
  readonly paginaChange = output<PaginazioneModel>();

  /**
   * Solo la richiesta: la tabella non chiede conferma e non chiama il backend.
   * Se ne occupa il componente pagina, che è anche l'unico a poter ricaricare le righe.
   */
  readonly elimina = output<DipendenteModel>();

  protected readonly colonne = [
    'cognome',
    'nome',
    'codiceFiscale',
    'tipoContratto',
    'dataScadenza',
    'azioni',
  ];
  protected readonly opzioniRighePerPagina = OPZIONI_RIGHE_PER_PAGINA;
  protected readonly SENZA_SCADENZA = SENZA_SCADENZA;

  /**
   * Le righe passano da plainToInstance, quindi a ogni ricerca sono oggetti nuovi:
   * senza questo la tabella si ricostruirebbe tutta anche a parità di contenuto.
   */
  protected readonly tracciaDipendente = (_: number, dipendente: DipendenteModel): number =>
    dipendente.id;

  /**
   * Il backend salva la stringa grezza (`INDETERMINATO`): in tabella si mostra
   * scritta come si legge. Un valore fuori dai due previsti si mostra com'è,
   * invece di sparire: sulla colonna il database non ha vincoli.
   */
  protected etichettaContratto(tipoContratto: string): string {
    return ETICHETTE_TIPO_CONTRATTO[tipoContratto as TipoContratto] ?? tipoContratto;
  }

  protected onPagina(evento: PageEvent): void {
    this.paginaChange.emit({
      numeroPagina: evento.pageIndex,
      righePerPagina: evento.pageSize,
    });
  }
}
