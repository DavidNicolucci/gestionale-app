import { FormGroup } from '@angular/forms';
import { plainToInstance } from 'class-transformer';
import { NuovoSitoFormInterface } from '../interfaces/nuovo-sito-form.interface';

/**
 * Body di creazione sito (`SitoRequest`). La trasformazione form -> backend sta
 * qui e non nel componente: la pagina passa il form e basta.
 */
export class SalvaSitoRequestModel {
  nome!: string;
  indirizzo?: string;
  clienteId!: number;

  /**
   * `getRawValue()` e non `.value`: include anche i control disabilitati, che
   * altrimenti sparirebbero dal body.
   */
  static generateModel(form: FormGroup<NuovoSitoFormInterface>): SalvaSitoRequestModel {
    const raw = form.getRawValue();
    const indirizzo = raw.indirizzo?.trim();

    return plainToInstance(SalvaSitoRequestModel, {
      nome: raw.nome?.trim() ?? '',
      // Non si manda una stringa vuota: sul database la colonna è NULL quando
      // l'indirizzo non c'è, e "" sarebbe un indirizzo vuoto invece che assente.
      indirizzo: indirizzo || undefined,
      clienteId: raw.clienteId ?? 0,
    });
  }
}
