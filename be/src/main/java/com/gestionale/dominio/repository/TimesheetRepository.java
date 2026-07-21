package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.entity.Timesheet;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TimesheetRepository implements PanacheRepository<Timesheet> {

    // Il DTO mostra nome del dipendente e nome del sito. Con un normale listAll()
    // Hibernate li leggerebbe uno per volta, cioe' 2 query in piu' per ogni riga.
    // Con JOIN FETCH li carica tutti insieme: una query sola.
    public List<Timesheet> listaConRelazioni() {
        return find("SELECT t FROM Timesheet t JOIN FETCH t.dipendente JOIN FETCH t.sito").list();
    }

    // Come sopra ma su una riga sola: evita le 2 query in piu'.
    // Torna Optional perche' l'id potrebbe non esistere: il service lo trasforma in 404.
    public Optional<Timesheet> perIdConRelazioni(Long id) {
        return find("SELECT t FROM Timesheet t JOIN FETCH t.dipendente JOIN FETCH t.sito WHERE t.id = ?1", id)
                .firstResultOptional();
    }

    // Somma le ore di un dipendente in un periodo. La somma la fa il database:
    // caricare migliaia di righe in Java per ottenere un solo numero sarebbe uno spreco.
    public BigDecimal sommaOrePeriodo(Long dipendenteId, LocalDate da, LocalDate a) {
        BigDecimal somma = getEntityManager()
                .createQuery("""
                        SELECT SUM(t.oreLavorate) FROM Timesheet t
                        WHERE t.dipendente.id = ?1 AND t.dataLavoro BETWEEN ?2 AND ?3
                        """, BigDecimal.class)
                .setParameter(1, dipendenteId)
                .setParameter(2, da)
                .setParameter(3, a)
                .getSingleResult();

        // Se non trova righe la somma torna null, non 0: senza questo controllo
        // chi chiama il metodo si ritroverebbe un NullPointerException.
        return somma != null ? somma : BigDecimal.ZERO;
    }
}
