package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.entity.Dipendente;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped     // bean CDI singleton, iniettabile nei service
public class DipendenteRepository implements PanacheRepository<Dipendente> {

    // PanacheRepository fornisce GIÀ: persist, findById, listAll, delete, count...
    // Qui aggiungiamo solo le query specifiche del dominio.

    public List<Dipendente> cercaPerCognome(String cognome) {
        return list("cognome", cognome);
        // "list" è Panache: traduce in "WHERE cognome = ?1". Niente JPQL a mano per i casi semplici.
    }
}