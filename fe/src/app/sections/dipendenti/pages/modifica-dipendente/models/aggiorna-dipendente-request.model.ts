import { FormGroup } from '@angular/forms';
import { plainToInstance } from 'class-transformer';
import { DipendenteFormInterface } from '../../../interfaces/dipendente-form.interface';
import { TipoContratto } from '../../../enums/tipo-contratto.enum';
import { aDataIso } from '../../../../../shared/utils/data.util';

/**
 * Body di modifica dipendente (`DipendentePatchRequest`).
 *
 * È una PATCH, dove un campo assente vuol dire "non toccarlo", ma noi li mandiamo
 * tutti lo stesso: il form li ha tutti a schermo e tutti compilati, quindi mandare
 * solo quelli cambiati vorrebbe dire confrontare i valori con quelli di partenza per
 * risparmiare qualche byte, e sbagliare quel confronto è più facile che scriverlo.
 */
export class AggiornaDipendenteRequestModel {
  nome!: string;
  cognome!: string;
  codiceFiscale!: string;
  dataNascita!: string;
  nazionalita!: string;
  tipoContratto!: string;
  dataAssunzione!: string;
  dataScadenza?: string;
  /**
   * L'eccezione alla regola di sopra. Una scadenza da togliere non si può esprimere
   * mandando `dataScadenza` vuota: sarebbe indistinguibile da "non toccarla", ed è
   * l'unico modo che il backend ha di capire che il contratto diventa a tempo
   * indeterminato.
   */
  rimuoviScadenza!: boolean;

  /**
   * `getRawValue()` e non `.value`: include anche i control disabilitati, che
   * altrimenti sparirebbero dal body.
   */
  static generateModel(
    form: FormGroup<DipendenteFormInterface>,
  ): AggiornaDipendenteRequestModel {
    const raw = form.getRawValue();
    const aTermine = raw.tipoContratto === TipoContratto.DETERMINATO;

    return plainToInstance(AggiornaDipendenteRequestModel, {
      nome: raw.nome?.trim() ?? '',
      cognome: raw.cognome?.trim() ?? '',
      // In maiuscolo come alla creazione: sul database i codici fiscali stanno così,
      // e il controllo di unicità del backend confronta le stringhe senza normalizzarle.
      codiceFiscale: raw.codiceFiscale?.trim().toUpperCase() ?? '',
      dataNascita: aDataIso(raw.dataNascita) ?? '',
      nazionalita: raw.nazionalita?.trim() ?? '',
      tipoContratto: raw.tipoContratto ?? '',
      dataAssunzione: aDataIso(raw.dataAssunzione) ?? '',
      dataScadenza: aTermine ? aDataIso(raw.dataScadenza) : undefined,
      // I due campi si muovono insieme e in versi opposti: o c'è una scadenza, o si
      // sta chiedendo di toglierla.
      rimuoviScadenza: !aTermine,
    });
  }
}
