import { FormControl } from '@angular/forms';

export interface NuovoClienteFormInterface {
  ragioneSociale: FormControl<string | null>;
  partitaIva: FormControl<string | null>;
  indirizzo: FormControl<string | null>;
}
