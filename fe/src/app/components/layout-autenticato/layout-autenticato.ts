import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AssistenteWidget } from '../../shared/components/assistente-widget/assistente-widget';

/**
 * Guscio delle pagine che richiedono il login: dentro ci sta la sezione di
 * turno, fuori quello che deve restare a schermo comunque.
 *
 * È il punto unico dove montare l'assistente: se ogni sezione se lo importasse
 * per conto suo, la conversazione ripartirebbe da zero a ogni cambio pagina e
 * ogni pagina nuova sarebbe un'occasione per dimenticarselo. Il login resta
 * fuori da questo albero, quindi lì l'assistente non compare.
 */
@Component({
  selector: 'app-layout-autenticato',
  imports: [RouterOutlet, AssistenteWidget],
  templateUrl: './layout-autenticato.html',
  styleUrl: './layout-autenticato.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutAutenticato {}
