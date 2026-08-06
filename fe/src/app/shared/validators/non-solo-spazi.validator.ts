import { AbstractControl, ValidationErrors } from '@angular/forms';

/**
 * `Validators.required` accetta una stringa di soli spazi, il `@NotBlank` del
 * backend no: questo chiude la differenza, e vale per qualsiasi campo di testo
 * obbligatorio dell'applicazione.
 *
 * Riusa la chiave `required` invece di inventarne una nuova, così i template
 * hanno un messaggio solo da mostrare per "campo obbligatorio".
 */
export function nonSoloSpazi(control: AbstractControl): ValidationErrors | null {
  const valore = control.value as string | null;
  return valore !== null && valore.trim() === '' ? { required: true } : null;
}
