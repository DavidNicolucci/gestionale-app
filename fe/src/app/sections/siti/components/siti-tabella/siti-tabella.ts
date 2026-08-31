import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTooltip } from '@angular/material/tooltip';
import { SitoModel } from '../../interfaces/sito.model';
import {
  OPZIONI_RIGHE_PER_PAGINA,
  PaginazioneModel,
  RIGHE_PER_PAGINA_DEFAULT,
} from '../../../../shared/interfaces/paginazione.model';
import { paginatoreItaliano } from '../../../../shared/services/paginatore-italiano';

/** Mostrato al posto di un indirizzo che non c'è: sul backend è l'unico campo facoltativo. */
const SENZA_INDIRIZZO = 'Non indicato';

/** Tabella dei siti trovati: riceve le righe già pronte dal componente pagina. */
@Component({
  selector: 'app-siti-tabella',
  imports: [MatIcon, MatIconButton, MatTableModule, MatPaginatorModule, MatTooltip],
  templateUrl: './siti-tabella.html',
  styleUrl: './siti-tabella.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: MatPaginatorIntl, useFactory: paginatoreItaliano }],
})
export class SitiTabella {
  readonly siti = input.required<SitoModel[]>();
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
  readonly elimina = output<SitoModel>();

  protected readonly colonne = ['nome', 'indirizzo', 'cliente', 'azioni'];
  protected readonly opzioniRighePerPagina = OPZIONI_RIGHE_PER_PAGINA;
  protected readonly SENZA_INDIRIZZO = SENZA_INDIRIZZO;

  /**
   * Le righe passano da plainToInstance, quindi a ogni ricerca sono oggetti nuovi:
   * senza questo la tabella si ricostruirebbe tutta anche a parità di contenuto.
   */
  protected readonly tracciaSito = (_: number, sito: SitoModel): number => sito.id;

  protected onPagina(evento: PageEvent): void {
    this.paginaChange.emit({
      numeroPagina: evento.pageIndex,
      righePerPagina: evento.pageSize,
    });
  }
}
