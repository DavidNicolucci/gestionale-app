package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.entity.Dipendente;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped     // bean CDI singleton, iniettabile nei service
public class DipendenteRepository implements PanacheRepository<Dipendente> {

    // PanacheRepository fornisce GIÀ: persist, findById, listAll, delete, count...
    // Qui aggiungiamo solo le query specifiche del dominio.

    public List<Dipendente> cercaPerCognome(String cognome) {
        return list("cognome", cognome);
        // "list" è Panache: traduce in "WHERE cognome = ?1". Niente JPQL a mano per i casi semplici.
    }

    // Optional al posto di null: la firma del metodo DICE che il dipendente puo' non esserci,
    // e il compilatore obbliga il chiamante a decidere cosa fare in quel caso.
    // firstResultOptional() e' la variante Panache di firstResult() che non restituisce mai null.
    public Optional<Dipendente> perCodiceFiscale(String cf) {
        return find("codiceFiscale", cf).firstResultOptional();
    }

    // Ricerca per nome, cognome o nome completo.
    // TUTTI i dipendenti con listAll() e poi filtrava in Java.
    public Optional<Dipendente> perNominativo(String nominativo) {
        String cercato = nominativo.trim();
        List<Dipendente> risultati = find(
                "nome = ?1 or cognome = ?1 or concat(nome, ' ', cognome) = ?1", cercato)
                .range(0, 1)  // ne bastano 2 per capire se c'è ambiguità
                .list();
        return risultati.size() == 1 ? Optional.of(risultati.get(0)) : Optional.empty();
    }
}
