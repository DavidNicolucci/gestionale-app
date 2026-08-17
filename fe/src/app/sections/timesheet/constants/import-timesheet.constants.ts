/**
 * Vincoli dell'import timesheet, gli stessi che il backend applica sul file
 * caricato (`ImportResource` + `ImportConsumer`): stanno qui e non nel
 * componente perché valgono per chiunque carichi un file, non solo per la
 * schermata di upload.
 */

/** I due formati che Apache POI apre lato backend (`WorkbookFactory.create`). */
export const ESTENSIONI_EXCEL = ['.xlsx', '.xls'] as const;

/**
 * Valore dell'attributo `accept` dell'input file. È solo un suggerimento del
 * browser (dal dialogo si può sempre scegliere "tutti i file", e il drag & drop
 * lo ignora del tutto): la validazione vera sta nel query service.
 */
export const ACCEPT_EXCEL = ESTENSIONI_EXCEL.join(',');

/**
 * Limite di default di Quarkus (`quarkus.http.limits.max-body-size`, 10 MB):
 * oltre questa soglia la POST viene chiusa prima ancora di arrivare al resource,
 * quindi conviene fermare il file qui e dirlo subito.
 */
export const DIMENSIONE_MAX_BYTE = 10 * 1024 * 1024;
