/**
 * Testi degli errori di validazione del form. Non stanno in nessuna delle due
 * classi perché le riguardano entrambe: il form service produce gli errori, il
 * componente li mostra.
 *
 * Ce n'è uno solo perché i vincoli sono due, entrambi "obbligatorio": ripetere il
 * nome del campo in un messaggio che compare già sotto al campo non aggiunge niente.
 */
export const MESSAGGI_ERRORE = {
  OBBLIGATORIO: 'Campo obbligatorio',
} as const;
