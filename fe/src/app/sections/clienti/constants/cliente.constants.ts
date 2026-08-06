/**
 * Vincoli del dominio cliente, gli stessi che il backend applica sul
 * `ClienteRequest`: stanno qui e non in una pagina perché valgono per tutte
 * (creazione, modifica, filtri).
 */

/** `@Size(max = 20)` sulla partita IVA: meglio dirlo prima di prendere un 400. */
export const LUNGHEZZA_MAX_PARTITA_IVA = 20;
