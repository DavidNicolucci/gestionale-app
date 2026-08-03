import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { ClienteModel } from '../../interfaces/cliente.model';

/** Tabella dei clienti trovati: riceve le righe già pronte dal componente pagina. */
@Component({
  selector: 'app-clienti-tabella',
  imports: [MatTableModule],
  templateUrl: './clienti-tabella.html',
  styleUrl: './clienti-tabella.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientiTabella {
  readonly clienti = input.required<ClienteModel[]>();
  readonly loading = input(false);

  protected readonly colonne = ['ragioneSociale', 'partitaIva', 'indirizzo'];
}
