package com.gestionale.dominio.model.enums;

/**
 * Stato di un dipendente per come lo vedono l'interfaccia e l'assistente.
 *
 * Sul database non c'e': l'unica cosa salvata e' il flag "eliminato", il resto si
 * ricava dalla data di scadenza confrontata con oggi. Se lo salvassimo in colonna
 * servirebbe un lavoro notturno che gira i valori a mezzanotte, e ogni modifica alla
 * data di scadenza dovrebbe ricordarsi di aggiornarlo: due verita' che prima o poi
 * si scollano. Calcolarlo costa niente ed e' sempre giusto.
 */
public enum StatoDipendente {

    /** Sotto contratto, nessuna scadenza vicina. */
    ATTIVO,

    /** Sotto contratto ma il contratto finisce entro il preavviso: e' solo un avviso,
     *  a tutti gli effetti e' utilizzabile come un ATTIVO. */
    IN_SCADENZA,

    /** Contratto finito. Non si puo' piu' usare ne' modificare: serve un rinnovo. */
    SCADUTO,

    /** Cancellato logicamente. Fuori da anagrafica, ricerche e assistente, ma i suoi
     *  timesheet restano: le ore consuntivate sono un fatto, non spariscono. */
    ELIMINATO
}
