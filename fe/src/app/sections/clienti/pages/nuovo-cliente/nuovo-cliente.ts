import {ChangeDetectionStrategy, Component} from '@angular/core';
import {AppRoute} from '../../../../shared/enums/app-route.enum';
import {GoBack} from '../../../../shared/components/go-back/go-back';

@Component({
  selector: 'app-nuovo-cliente',
  imports: [GoBack],
  templateUrl: './nuovo-cliente.html',
  styleUrl: './nuovo-cliente.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NuovoCliente {
  /** Esposto al template per il pulsante "Indietro". */
  protected readonly AppRoute = AppRoute;
}
