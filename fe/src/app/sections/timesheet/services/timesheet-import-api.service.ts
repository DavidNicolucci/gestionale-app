import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { plainToInstance } from 'class-transformer';
import { ImportAvviatoModel } from '../interfaces/import-avviato.model';

@Injectable({ providedIn: 'root' })
export class TimesheetImportApiService {
  private readonly http = inject(HttpClient);

  /**
   * Carica il file Excel delle ore. Il backend lo salva su disco e risponde 202
   * subito, mettendo in coda l'elaborazione: la promise si risolve quando il
   * file è stato ricevuto, non quando le righe sono state importate.
   */
  async importa(file: File): Promise<ImportAvviatoModel> {
    const corpo = new FormData();
    // 'file' è il nome del campo atteso dal backend (`@RestForm("file")`).
    corpo.append('file', file, file.name);

    const post$ = this.http.post<ImportAvviatoModel>('/api/import/timesheet', corpo);
    return plainToInstance(ImportAvviatoModel, await firstValueFrom(post$));
  }
}
