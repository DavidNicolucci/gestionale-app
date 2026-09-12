package com.gestionale.dominio.imports;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ImportJobRepository implements PanacheRepository<ImportJob> {

    /**
     * Il job che tiene occupato questo contenuto, se c'e'.
     *
     * "Occupato" vuol dire in coda, in lavorazione o gia' importato: in tutti e tre i
     * casi ricaricare lo stesso file rifarebbe un lavoro gia' fatto o in corso.
     * Un file FALLITO o ABBANDONATO invece non occupa niente, e ricaricarlo e' proprio
     * quello che si fa dopo aver corretto il problema.
     */
    public Optional<ImportJob> perHashGiaPreso(String hash) {
        return find("fileHash = ?1 and stato in ?2",
                Sort.by("id", Sort.Direction.Descending),
                hash,
                List.of(StatoImport.ACCODATO, StatoImport.IN_CORSO, StatoImport.COMPLETATO))
                .firstResultOptional();
    }

    /**
     * Gli import che non sono andati a buon fine, dal piu' recente.
     *
     * Non solo i FALLITO: ci sono anche ACCODATO e IN_CORSO, che sono la forma silenziosa
     * dello stesso problema. Un job fermo in IN_CORSO da ore vuol dire che il consumer e'
     * morto a meta' lavoro senza riuscire a scrivere l'esito; uno fermo in ACCODATO che il
     * messaggio non e' mai stato consumato. Nessuno dei due produce un errore da nessuna
     * parte: se l'elenco mostrasse solo i FALLITO resterebbero invisibili esattamente come
     * lo erano i messaggi in DLQ.
     */
    public List<ImportJob> daGuardare(int limite) {
        return find("stato in ?1", Sort.by("id", Sort.Direction.Descending),
                List.of(StatoImport.FALLITO, StatoImport.IN_CORSO, StatoImport.ACCODATO))
                .range(0, limite - 1)
                .list();
    }

    public List<ImportJob> perStato(StatoImport stato, int limite) {
        return find("stato", Sort.by("id", Sort.Direction.Descending), stato)
                .range(0, limite - 1)
                .list();
    }

    public List<ImportJob> ultimi(int limite) {
        return findAll(Sort.by("id", Sort.Direction.Descending))
                .range(0, limite - 1)
                .list();
    }
}
