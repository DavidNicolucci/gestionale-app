/**
 * Domande proposte a chat vuota. Non sono decorative: servono a far capire subito
 * il raggio d'azione dell'assistente, che legge dipendenti, clienti, siti e ore
 * lavorate (le classi `*Tools` lato backend) e non sa fare altro. Se lì vengono
 * aggiunti strumenti nuovi, vanno aggiornate anche queste.
 */
export const SUGGERIMENTI_CHAT: readonly string[] = [
  'Chi sono i dipendenti registrati?',
  'Quante ore abbiamo fatto per Acme S.p.A. questo mese?',
  'Chi ha lavorato sul Cantiere Via Roma la settimana scorsa?',
  'Quali siti ha il cliente Acme S.p.A.?',
];
