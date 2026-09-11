import { Injectable } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { DipendenteFormInterface } from '../interfaces/dipendente-form.interface';
import { TipoContratto } from '../enums/tipo-contratto.enum';
import { LUNGHEZZA_CODICE_FISCALE } from '../constants/dipendente.constants';
import { nonSoloSpazi } from '../../../shared/validators/non-solo-spazi.validator';
import { daDataIso } from '../../../shared/utils/data.util';
import { DipendenteModel } from '../interfaces/dipendente.model';

/**
 * Costruisce e popola il form del dipendente: nessuno stato, nessuna chiamata.
 * Lo usano sia la creazione sia la modifica — sono lo stesso modulo con lo stesso
 * insieme di regole, e tenerne due copie vorrebbe dire correggerle due volte.
 *
 * È fornito dal componente pagina (niente `providedIn`), quindi non sopravvive
 * alla navigazione.
 */
@Injectable()
export class DipendenteFormService {
  /**
   * Tutti obbligatori tranne la scadenza, come sul `DipendenteRequest`. Il codice
   * fiscale ha min e max uguali perché il backend usa `@Size(min = 16, max = 16)`:
   * non è "al massimo 16", è "esattamente 16".
   */
  createMainForm(): FormGroup<DipendenteFormInterface> {
    return new FormGroup<DipendenteFormInterface>({
      nome: new FormControl(null, [Validators.required, nonSoloSpazi]),
      cognome: new FormControl(null, [Validators.required, nonSoloSpazi]),
      codiceFiscale: new FormControl(null, [
        Validators.required,
        nonSoloSpazi,
        Validators.minLength(LUNGHEZZA_CODICE_FISCALE),
        Validators.maxLength(LUNGHEZZA_CODICE_FISCALE),
      ]),
      dataNascita: new FormControl(null, [Validators.required]),
      nazionalita: new FormControl(null, [Validators.required, nonSoloSpazi]),
      tipoContratto: new FormControl(null, [Validators.required]),
      dataAssunzione: new FormControl(null, [Validators.required]),
      // Parte senza validator: le sue regole dipendono dal tipo di contratto,
      // che all'apertura non è ancora stato scelto.
      dataScadenza: new FormControl(null),
    });
  }

  /**
   * La scadenza segue il tipo di contratto: obbligatoria per un contratto a
   * termine, inesistente per un indeterminato (sul database la colonna è NULL
   * proprio in quel caso).
   *
   * Va richiamato a ogni cambio di `tipoContratto`: i validator di un control non
   * si rivalutano da soli quando cambia un altro campo.
   */
  aggiornaScadenza(
    form: FormGroup<DipendenteFormInterface>,
    tipoContratto: TipoContratto | null,
  ): void {
    const scadenza = form.controls.dataScadenza;

    if (tipoContratto === TipoContratto.DETERMINATO) {
      scadenza.setValidators([Validators.required]);
      scadenza.updateValueAndValidity();
      return;
    }

    // Passando a indeterminato una data già scelta resterebbe nel form e
    // finirebbe nel body: si svuota, oltre a togliere il validator.
    scadenza.clearValidators();
    scadenza.setValue(null);
    scadenza.updateValueAndValidity();
  }

  /**
   * Riempie il form con un dipendente già salvato: è quello che serve alla pagina
   * di modifica, dove i campi non nascono vuoti.
   *
   * Il tipo di contratto NON si ricava dalla presenza della scadenza: sul database
   * `tipo_contratto` è una colonna sua, senza vincoli, e potrebbe contenere un valore
   * fuori dai due previsti. Lo passiamo com'è e lasciamo che sia il `mat-select` a
   * non trovarlo, invece di riscrivere di nascosto il dato di qualcun altro.
   *
   * `aggiornaScadenza` va richiamato dopo, come alla creazione: i validator della
   * scadenza dipendono dal contratto e non si sistemano da soli con un `setValue`.
   */
  popolaForm(form: FormGroup<DipendenteFormInterface>, dipendente: DipendenteModel): void {
    form.setValue({
      nome: dipendente.nome,
      cognome: dipendente.cognome,
      codiceFiscale: dipendente.codiceFiscale,
      dataNascita: daDataIso(dipendente.dataNascita),
      nazionalita: dipendente.nazionalita,
      tipoContratto: dipendente.tipoContratto as TipoContratto,
      dataAssunzione: daDataIso(dipendente.dataAssunzione),
      dataScadenza: daDataIso(dipendente.dataScadenza),
    });

    // Senza questo il form nasce "pristine" e resta tale finché non si tocca un
    // campo: è esattamente quello che vogliamo, perché "Salva" deve accendersi solo
    // dopo una modifica vera. Lo scriviamo per dire che è voluto e non dimenticato.
    form.markAsPristine();
  }

  /**
   * Errori di validazione arrivati dal backend (`fieldErrors`) portati sui
   * control: l'utente li legge sotto al campo sbagliato invece che in cima alla
   * pagina. Restano finché il campo non viene ritoccato, perché il `valueChanges`
   * di Angular rivaluta i soli validator sincroni e ripulisce `backend`.
   */
  applicaErroriBackend(
    form: FormGroup<DipendenteFormInterface>,
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
