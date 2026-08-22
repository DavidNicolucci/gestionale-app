import { FormControl } from '@angular/forms';
import { TipoContratto } from '../../../enums/tipo-contratto.enum';

/**
 * Le date sono `Date` e non stringhe: è il tipo con cui lavora il datepicker di
 * Material. La conversione nel `yyyy-MM-dd` del backend avviene una volta sola,
 * quando si compone il body (`SalvaDipendenteRequestModel`).
 */
export interface NuovoDipendenteFormInterface {
  nome: FormControl<string | null>;
  cognome: FormControl<string | null>;
  codiceFiscale: FormControl<string | null>;
  dataNascita: FormControl<Date | null>;
  nazionalita: FormControl<string | null>;
  tipoContratto: FormControl<TipoContratto | null>;
  dataAssunzione: FormControl<Date | null>;
  /** Solo per i contratti a termine: un indeterminato non scade. */
  dataScadenza: FormControl<Date | null>;
}
