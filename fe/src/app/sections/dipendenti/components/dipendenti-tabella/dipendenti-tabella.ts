import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTooltip } from '@angular/material/tooltip';
import { DipendenteModel } from '../../interfaces/dipendente.model';
import { ETICHETTE_TIPO_CONTRATTO, TipoContratto } from '../../enums/tipo-contratto.enum';
import {
  CLASSI_STATO_DIPENDENTE,
  ETICHETTE_STATO_DIPENDENTE,
  StatoDipendente,
  utilizzabile,
} from '../../enums/stato-dipendente.enum';
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
  imports: [DatePipe, MatIcon, MatIconButton, MatTableModule, MatPaginatorModule, MatTooltip],
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

  /** Rimette in anagrafica un eliminato. Non gli tocca il contratto. */
  readonly ripristina = output<DipendenteModel>();

  /** Sposta in avanti la scadenza: è l'unico modo di riusare uno scaduto. */
  readonly rinnova = output<DipendenteModel>();

  protected readonly colonne = [
    'cognome',
    'nome',
    'codiceFiscale',
    'tipoContratto',
    'dataAssunzione',
    'dataScadenza',
    'stato',
    'azioni',
  ];
  protected readonly opzioniRighePerPagina = OPZIONI_RIGHE_PER_PAGINA;
  protected readonly SENZA_SCADENZA = SENZA_SCADENZA;
  protected readonly StatoDipendente = StatoDipendente;

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

  protected etichettaStato(dipendente: DipendenteModel): string {
    return ETICHETTE_STATO_DIPENDENTE[dipendente.stato];
  }

  /** `pastiglia pastiglia--scaduto`: il modificatore lo decide lo stato. */
  protected classePastiglia(dipendente: DipendenteModel): string {
    return `pastiglia pastiglia--${CLASSI_STATO_DIPENDENTE[dipendente.stato]}`;
  }

  /**
   * Attenua la riga di chi non si può più usare. È l'unica differenza visiva oltre
   * alla pastiglia: sono dati veri e vanno letti, solo che non sono più operativi.
   */
  protected utilizzabile(dipendente: DipendenteModel): boolean {
    return utilizzabile(dipendente.stato);
  }

  protected onPagina(evento: PageEvent): void {
    this.paginaChange.emit({
      numeroPagina: evento.pageIndex,
      righePerPagina: evento.pageSize,
    });
  }
}
