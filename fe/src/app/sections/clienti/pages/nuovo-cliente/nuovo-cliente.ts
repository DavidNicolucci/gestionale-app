import {ChangeDetectionStrategy, Component} from '@angular/core';

@Component({
  selector: 'app-nuovo-cliente',
  imports: [],
  templateUrl: './nuovo-cliente.html',
  styleUrl: './nuovo-cliente.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NuovoCliente {}
