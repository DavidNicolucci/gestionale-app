import { RicercaPaginataRequestModel } from '../../../shared/interfaces/pagina-response.model';

/**
 * Filtri della ricerca clienti (`ClienteRicercaRequest`).
 * Un campo assente non filtra; ragioneSociale e partitaIva sono ricerche "contiene".
 */
export interface ClienteRicercaRequestModel extends RicercaPaginataRequestModel {
  ragioneSociale?: string;
  partitaIva?: string;
}
