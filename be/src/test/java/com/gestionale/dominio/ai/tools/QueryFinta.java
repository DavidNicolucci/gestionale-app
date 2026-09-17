package com.gestionale.dominio.ai.tools;

import io.quarkus.hibernate.orm.panache.PanacheQuery;

import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Un PanacheQuery finto che tiene una lista in memoria.
 *
 * Serve perche' i repository tornano la query e non la lista (chi chiama decide se
 * contarla o impaginarla), ma per provare gli strumenti non vogliamo un database:
 * quello che vogliamo provare e' il testo che ne esce. L'interfaccia ha una ventina
 * di metodi e qui ne servono tre, quindi un proxy dinamico costa meno di una classe
 * piena di metodi che lanciano eccezioni.
 */
final class QueryFinta {

    private QueryFinta() {
    }

    @SuppressWarnings("unchecked")
    static <T> PanacheQuery<T> di(List<T> elementi) {
        return (PanacheQuery<T>) Proxy.newProxyInstance(
                PanacheQuery.class.getClassLoader(),
                new Class<?>[]{PanacheQuery.class},
                (proxy, metodo, argomenti) -> switch (metodo.getName()) {
                    // range() e page() tornano la query stessa: e' cosi' che si
                    // incatenano nel codice vero (elenco(...).range(0, 49).list()).
                    case "range", "page" -> proxy;
                    case "list" -> elementi;
                    case "count" -> (long) elementi.size();
                    case "toString" -> "QueryFinta(" + elementi.size() + ")";
                    default -> throw new UnsupportedOperationException(
                            "QueryFinta non sa fare " + metodo.getName());
                });
    }
}
