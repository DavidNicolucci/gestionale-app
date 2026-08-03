import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { ClienteModel } from '../interfaces/cliente.model';
import { ClienteRicercaRequestModel } from '../interfaces/cliente-ricerca-request.model';
import { PaginaResponseModel } from '../../../shared/interfaces/pagina-response.model';

@Injectable({ providedIn: 'root' })
export class ClientApiiService {
  private readonly http = inject(HttpClient);

  /**
   * Ricerca paginata dei clienti. È una POST perché i filtri stanno nel body:
   * body vuoto = prima pagina senza filtri.
   */
  async cerca(filtri: ClienteRicercaRequestModel): Promise<PaginaResponseModel<ClienteModel>> {
    const post$= this.http.post<PaginaResponseModel<ClienteModel>>('/api/clienti/ricerca', filtri)
    const response = await firstValueFrom(post$);
    return {...response, risultati: plainToInstance(ClienteModel, response.risultati ?? [])};
  }
}
