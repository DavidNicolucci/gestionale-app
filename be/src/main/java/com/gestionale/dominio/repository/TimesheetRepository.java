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

    // Dipendente e Sito sono @ManyToOne(LAZY): un semplice listAll() li lascerebbe come proxy,
    // e il mapper del DTO (che legge nome e cognome) li risolverebbe UNO ALLA VOLTA
    // -> 1 query per la lista + 2 per ogni riga = N+1.
    // La JOIN FETCH li carica nella STESSA query: 1 query totale.
    public List<Timesheet> listaConRelazioni() {
        return find("SELECT t FROM Timesheet t JOIN FETCH t.dipendente JOIN FETCH t.sito").list();
    }

    // Stesso motivo del metodo sopra, sul singolo: evita le 2 query extra sui proxy.
    // Optional perche' l'id potrebbe non esistere: sta al service tradurlo in 404.
    public Optional<Timesheet> perIdConRelazioni(Long id) {
        return find("SELECT t FROM Timesheet t JOIN FETCH t.dipendente JOIN FETCH t.sito WHERE t.id = ?1", id)
                .firstResultOptional();
    }

    // Somma le ore di un dipendente in un intervallo di date.
    // La somma la fa il DATABASE: caricare le righe per sommarle in Java significherebbe
    // istanziare migliaia di entity per ottenere un solo numero.
    // Sfrutta l'indice ix_timesheet_dipendente (dipendente_id, data_lavoro).
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

        // SUM su zero righe restituisce NULL in SQL, non 0: senza questo, "nessuna ora
        // registrata" diventerebbe un NullPointerException nel chiamante.
        return somma != null ? somma : BigDecimal.ZERO;
    }
}
