import { FormGroup } from '@angular/forms';
import { plainToInstance } from 'class-transformer';
import { DipendenteFormInterface } from '../../../interfaces/dipendente-form.interface';
import { TipoContratto } from '../../../enums/tipo-contratto.enum';
import { aDataIso } from '../../../../../shared/utils/data.util';

/**
 * Body di creazione dipendente (`DipendenteRequest`). La trasformazione
 * form → backend sta qui e non nel componente: la pagina passa il form e basta.
 */
export class SalvaDipendenteRequestModel {
  nome!: string;
  cognome!: string;
  codiceFiscale!: string;
  dataNascita!: string;
  nazionalita!: string;
  tipoContratto!: string;
  dataAssunzione!: string;
  dataScadenza?: string;

  /**
   * `getRawValue()` e non `.value`: include anche i control disabilitati, che
   * altrimenti sparirebbero dal body.
   */
  static generateModel(form: FormGroup<DipendenteFormInterface>): SalvaDipendenteRequestModel {
    const raw = form.getRawValue();
    const aTermine = raw.tipoContratto === TipoContratto.DETERMINATO;

    return plainToInstance(SalvaDipendenteRequestModel, {
      nome: raw.nome?.trim() ?? '',
      cognome: raw.cognome?.trim() ?? '',
      // In maiuscolo: sul database i codici fiscali stanno così, e il controllo di
      // unicità del backend confronta le stringhe senza normalizzarle.
      codiceFiscale: raw.codiceFiscale?.trim().toUpperCase() ?? '',
      dataNascita: aDataIso(raw.dataNascita) ?? '',
      nazionalita: raw.nazionalita?.trim() ?? '',
      tipoContratto: raw.tipoContratto ?? '',
      dataAssunzione: aDataIso(raw.dataAssunzione) ?? '',
      // Un indeterminato non scade. Il campo viene già svuotato quando si cambia
      // contratto, ma il controllo si rifà qui: è questo il punto che decide cosa
      // parte davvero verso il backend.
      dataScadenza: aTermine ? aDataIso(raw.dataScadenza) : undefined,
    });
  }
}
