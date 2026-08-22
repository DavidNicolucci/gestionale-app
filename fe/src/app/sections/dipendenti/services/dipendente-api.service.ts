import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { DipendenteModel } from '../interfaces/dipendente.model';
import { DipendenteRicercaRequestModel } from '../interfaces/dipendente-ricerca-request.model';
import { SalvaDipendenteRequestModel } from '../pages/nuovo-dipendente/models/salva-dipendente-request.model';
import { PaginaResponseModel } from '../../../shared/interfaces/pagina-response.model';

@Injectable({ providedIn: 'root' })
export class DipendenteApiService {
  private readonly http = inject(HttpClient);

  /**
   * Ricerca paginata dei dipendenti. È una POST perché i filtri stanno nel body:
   * body vuoto = prima pagina senza filtri.
   */
  async cerca(
    filtri: DipendenteRicercaRequestModel,
  ): Promise<PaginaResponseModel<DipendenteModel>> {
    const post$ = this.http.post<PaginaResponseModel<DipendenteModel>>(
      '/api/dipendenti/ricerca',
      filtri,
    );
    const response = await firstValueFrom(post$);
    return { ...response, risultati: plainToInstance(DipendenteModel, response.risultati ?? []) };
  }

  /** Crea un dipendente e restituisce quello salvato, con l'id assegnato dal backend. */
  async crea(dipendente: SalvaDipendenteRequestModel): Promise<DipendenteModel> {
    const post$ = this.http.post<DipendenteModel>('/api/dipendenti', dipendente);
    return plainToInstance(DipendenteModel, await firstValueFrom(post$));
  }

  /**
   * Elimina un dipendente. Il backend risponde 204 senza corpo, quindi non c'è
   * niente da restituire: è andata bene se la Promise non rifiuta.
   */
  async elimina(id: number): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`/api/dipendenti/${id}`));
  }
}
