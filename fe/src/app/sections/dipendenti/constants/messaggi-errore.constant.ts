import { LUNGHEZZA_CODICE_FISCALE } from './dipendente.constants';

/**
 * Testi degli errori di validazione del form. Non stanno in nessuna delle due
 * classi perché le riguardano entrambe: il form service produce gli errori, il
 * componente li mostra.
 *
 * A differenza dei clienti qui il messaggio "obbligatorio" è uno solo: i campi
 * richiesti sono sette, e ripetere il nome del campo in un messaggio che compare
 * già sotto al campo non aggiunge niente.
 */
export const MESSAGGI_ERRORE = {
  OBBLIGATORIO: 'Campo obbligatorio',
  CODICE_FISCALE_LUNGHEZZA: `Il codice fiscale deve essere di ${LUNGHEZZA_CODICE_FISCALE} caratteri`,
  DATA_NASCITA_NON_PASSATA: 'La data di nascita deve essere nel passato',
  DATA_NON_VALIDA: 'Data non valida',
} as const;
