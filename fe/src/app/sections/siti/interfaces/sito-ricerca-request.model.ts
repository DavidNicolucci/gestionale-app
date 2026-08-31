import { RicercaPaginataRequestModel } from '../../../shared/interfaces/pagina-response.model';

/**
 * Filtri della ricerca siti (`SitoRicercaRequest`). Un campo assente non filtra.
 * Il nome è una ricerca "contiene", il cliente un confronto esatto sull'id: sono
 * gli unici due filtri che il backend conosce.
 */
export interface SitoRicercaRequestModel extends RicercaPaginataRequestModel {
  nome?: string;
  clienteId?: number;
}
