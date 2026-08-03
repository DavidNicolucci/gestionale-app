import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { ClienteModel } from '../interfaces/cliente.model';
import { ClienteRicercaRequestModel } from '../interfaces/cliente-ricerca-request.model';
import { PaginaResponseModel } from '../../../shared/interfaces/pagina-response.model';

@Injectable({ providedIn: 'root' })
export class ClientiService {
  private readonly http = inject(HttpClient);

  /**
   * Ricerca paginata dei clienti. È una POST perché i filtri stanno nel body:
   * body vuoto = prima pagina senza filtri.
   */
  cerca(filtri: ClienteRicercaRequestModel = {}): Observable<PaginaResponseModel<ClienteModel>> {
    return this.http
      .post<PaginaResponseModel<ClienteModel>>('/api/clienti/ricerca', filtri)
      .pipe(
        map((pagina) => ({
          ...pagina,
          risultati: plainToInstance(ClienteModel, pagina.risultati ?? []),
        })),
      );
  }
}
