import { RicercaPaginataRequestModel } from '../../../shared/interfaces/pagina-response.model';

/**
 * Filtri della ricerca dipendenti (`DipendenteRicercaRequest`).
 * Un campo assente non filtra; sono tutte e tre ricerche "contiene".
 */
export interface DipendenteRicercaRequestModel extends RicercaPaginataRequestModel {
  nome?: string;
  cognome?: string;
  codiceFiscale?: string;
}
