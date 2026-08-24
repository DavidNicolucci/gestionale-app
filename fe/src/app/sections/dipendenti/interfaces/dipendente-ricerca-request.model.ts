import { RicercaPaginataRequestModel } from '../../../shared/interfaces/pagina-response.model';

/**
 * Filtri della ricerca dipendenti (`DipendenteRicercaRequest`).
 * Un campo assente non filtra; sono tutte e tre ricerche "contiene".
 */
export interface DipendenteRicercaRequestModel extends RicercaPaginataRequestModel {
  nome?: string;
  cognome?: string;
  codiceFiscale?: string;
  /**
   * Non è un filtro come gli altri tre: allarga il risultato invece di restringerlo.
   * Assente o false, gli eliminati restano fuori. Gli scaduti ci sono sempre — si
   * vedono in grigio in tabella e non serve chiederli.
   */
  includiEliminati?: boolean;
}
