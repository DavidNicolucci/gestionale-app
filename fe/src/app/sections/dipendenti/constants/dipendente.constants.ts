/**
 * Vincoli del dominio dipendente, gli stessi che il backend applica sul
 * `DipendenteRequest`: stanno qui e non in una pagina perché valgono per tutte
 * (creazione, modifica, filtri).
 */

/** `@Size(min = 16, max = 16)`: il codice fiscale italiano è sempre di 16 caratteri. */
export const LUNGHEZZA_CODICE_FISCALE = 16;
