import { FormGroup } from '@angular/forms';
import { plainToInstance } from 'class-transformer';
import { NuovoClienteFormInterface } from '../interfaces/nuovo-cliente-form.interface';

/**
 * Body di creazione cliente (`ClienteRequest`). La trasformazione form → backend
 * sta qui e non nel componente: la pagina passa il form e basta.
 */
export class SalvaClienteRequestModel {
  ragioneSociale!: string;
  partitaIva?: string;
  indirizzo?: string;

  /**
   * `getRawValue()` e non `.value`: include anche i control disabilitati, che
   * altrimenti sparirebbero dal body.
   * I campi opzionali vuoti diventano `undefined` e non finiscono nel JSON, così
   * in tabella restano vuoti invece di mostrare stringhe vuote.
   */
  static generateModel(form: FormGroup<NuovoClienteFormInterface>): SalvaClienteRequestModel {
    const raw = form.getRawValue();

    return plainToInstance(SalvaClienteRequestModel, {
      ragioneSociale: raw.ragioneSociale?.trim() ?? '',
      partitaIva: raw.partitaIva?.trim() || undefined,
      indirizzo: raw.indirizzo?.trim() || undefined,
    });
  }
}
