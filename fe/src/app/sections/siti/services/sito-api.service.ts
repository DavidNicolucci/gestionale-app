import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { SitoModel } from '../interfaces/sito.model';
import { SitoRicercaRequestModel } from '../interfaces/sito-ricerca-request.model';
import { SalvaSitoRequestModel } from '../pages/nuovo-sito/models/salva-sito-request.model';
import { PaginaResponseModel } from '../../../shared/interfaces/pagina-response.model';

@Injectable({ providedIn: 'root' })
export class SitoApiService {
  private readonly http = inject(HttpClient);

  /**
   * Ricerca paginata dei siti. È una POST perché i filtri stanno nel body:
   * body vuoto = prima pagina senza filtri.
   */
  async cerca(filtri: SitoRicercaRequestModel): Promise<PaginaResponseModel<SitoModel>> {
    const post$ = this.http.post<PaginaResponseModel<SitoModel>>('/api/siti/ricerca', filtri);
    const response = await firstValueFrom(post$);
    return { ...response, risultati: plainToInstance(SitoModel, response.risultati ?? []) };
  }

  /** Crea un sito e restituisce quello salvato, con l'id assegnato dal backend. */
  async crea(sito: SalvaSitoRequestModel): Promise<SitoModel> {
    const post$ = this.http.post<SitoModel>('/api/siti', sito);
    return plainToInstance(SitoModel, await firstValueFrom(post$));
  }

  /**
   * Elimina un sito. Qui la cancellazione è fisica: la riga sparisce e non c'è un
   * ripristino. Il backend risponde 204 senza corpo, quindi non c'è niente da
   * restituire: è andata bene se la Promise non rifiuta.
   */
  async elimina(id: number): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`/api/siti/${id}`));
  }
}
