import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { ClienteModel } from '../interfaces/cliente.model';
import { ClienteRicercaRequestModel } from '../interfaces/cliente-ricerca-request.model';
import { SalvaClienteRequestModel } from '../pages/nuovo-cliente/models/salva-cliente-request.model';
import { PaginaResponseModel } from '../../../shared/interfaces/pagina-response.model';

@Injectable({ providedIn: 'root' })
export class ClientApiService {
  private readonly http = inject(HttpClient);

  /**
   * Ricerca paginata dei clienti. È una POST perché i filtri stanno nel body:
   * body vuoto = prima pagina senza filtri.
   */
  async cerca(filtri: ClienteRicercaRequestModel): Promise<PaginaResponseModel<ClienteModel>> {
    const post$ = this.http.post<PaginaResponseModel<ClienteModel>>('/api/clienti/ricerca', filtri);
    const response = await firstValueFrom(post$);
    return { ...response, risultati: plainToInstance(ClienteModel, response.risultati ?? []) };
  }

  /** Crea un cliente e restituisce quello salvato, con l'id assegnato dal backend. */
  async crea(cliente: SalvaClienteRequestModel): Promise<ClienteModel> {
    const post$ = this.http.post<ClienteModel>('/api/clienti', cliente);
    return plainToInstance(ClienteModel, await firstValueFrom(post$));
  }

  /**
   * Elimina un cliente. Il backend risponde 204 senza corpo, quindi non c'è niente
   * da restituire: è andata bene se la Promise non rifiuta.
   */
  async elimina(id: number): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`/api/clienti/${id}`));
  }
}
