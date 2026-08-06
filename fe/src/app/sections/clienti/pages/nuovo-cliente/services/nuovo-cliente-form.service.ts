import { Injectable } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { NuovoClienteFormInterface } from '../interfaces/nuovo-cliente-form.interface';
import { LUNGHEZZA_MAX_PARTITA_IVA } from '../../../constants/cliente.constants';
import { nonSoloSpazi } from '../../../../../shared/validators/non-solo-spazi.validator';

/**
 * Costruisce e popola il form: nessuno stato, nessuna chiamata. È fornito dal
 * componente pagina (niente `providedIn`), quindi non sopravvive alla navigazione.
 */
@Injectable()
export class NuovoClienteFormService {
  /** Solo la ragione sociale è obbligatoria, come sul `ClienteRequest`. */
  createMainForm(): FormGroup<NuovoClienteFormInterface> {
    return new FormGroup<NuovoClienteFormInterface>({
      ragioneSociale: new FormControl(null, [Validators.required, nonSoloSpazi]),
      partitaIva: new FormControl(null, [Validators.maxLength(LUNGHEZZA_MAX_PARTITA_IVA)]),
      indirizzo: new FormControl(null),
    });
  }

  /**
   * Errori di validazione arrivati dal backend (`fieldErrors`) portati sui
   * control: l'utente li legge sotto al campo sbagliato invece che in cima alla
   * pagina. Restano finché il campo non viene ritoccato, perché il `valueChanges`
   * di Angular rivaluta i soli validator sincroni e ripulisce `backend`.
   */
  applicaErroriBackend(
    form: FormGroup<NuovoClienteFormInterface>,
    fieldErrors: Record<string, string[]>,
  ): void {
    for (const [campo, messaggi] of Object.entries(fieldErrors)) {
      const control = form.get(campo);

      // Un campo che il form non ha (o senza messaggi) resta all'errore generico
      // della pagina: non c'è dove scriverlo.
      if (!control || !messaggi.length) {
        continue;
      }

      control.setErrors({ ...control.errors, backend: messaggi[0] });
      control.markAsTouched();
    }
  }
}
