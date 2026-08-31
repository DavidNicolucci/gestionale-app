/** Sito restituito dal backend (`SitoResponse`). */
export class SitoModel {
  id!: number;
  nome!: string;
  /** Facoltativo: sul `SitoRequest` è l'unico campo senza vincoli. */
  indirizzo?: string;
  clienteId!: number;
  /**
   * La ragione sociale arriva già dentro la risposta del sito: in tabella si legge
   * il cliente e non il suo id, senza una seconda chiamata per riga.
   */
  clienteRagioneSociale!: string;
}
