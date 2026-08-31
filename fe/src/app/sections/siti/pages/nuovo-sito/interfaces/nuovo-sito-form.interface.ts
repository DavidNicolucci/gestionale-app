import { FormControl } from '@angular/forms';

/**
 * Il cliente è un id e non un oggetto: è quello che il backend si aspetta nel
 * `SitoRequest`, e la tendina lavora direttamente sul valore che viaggia.
 */
export interface NuovoSitoFormInterface {
  nome: FormControl<string | null>;
  /** Facoltativo, come sul backend: un sito può essere censito senza indirizzo. */
  indirizzo: FormControl<string | null>;
  clienteId: FormControl<number | null>;
}
