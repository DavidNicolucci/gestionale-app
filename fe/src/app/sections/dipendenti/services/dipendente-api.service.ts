import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { DipendenteModel } from '../interfaces/dipendente.model';
import { DipendenteRicercaRequestModel } from '../interfaces/dipendente-ricerca-request.model';
import { RinnovoRequestModel } from '../interfaces/rinnovo-request.model';
import { ScadenzeModel } from '../interfaces/scadenze.model';
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
   * Elimina un dipendente. La cancellazione è logica: la riga resta sul database e
   * il dipendente si può ripristinare. Il backend risponde 204 senza corpo, quindi
   * non c'è niente da restituire: è andata bene se la Promise non rifiuta.
   */
  async elimina(id: number): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`/api/dipendenti/${id}`));
  }

  /**
   * Rimette in anagrafica un eliminato e restituisce com'è tornato. Non tocca il
   * contratto: se nel frattempo è scaduto, torna in stato SCADUTO.
   * Niente body: l'operazione non ha parametri, è l'indirizzo a dire cosa fa.
   */
  async ripristina(id: number): Promise<DipendenteModel> {
    const post$ = this.http.post<DipendenteModel>(`/api/dipendenti/${id}/ripristino`, {});
    return plainToInstance(DipendenteModel, await firstValueFrom(post$));
  }

  /** Sposta in avanti la scadenza del contratto. */
  async rinnova(id: number, richiesta: RinnovoRequestModel): Promise<DipendenteModel> {
    const post$ = this.http.post<DipendenteModel>(`/api/dipendenti/${id}/rinnovo`, richiesta);
    return plainToInstance(DipendenteModel, await firstValueFrom(post$));
  }

  /** Quanti contratti stanno per scadere, per l'avviso in cima alla pagina. */
  async scadenze(): Promise<ScadenzeModel> {
    return firstValueFrom(this.http.get<ScadenzeModel>('/api/dipendenti/scadenze'));
  }
}
