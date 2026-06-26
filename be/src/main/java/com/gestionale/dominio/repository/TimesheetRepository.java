package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.entity.Timesheet;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.LocalDate;

@ApplicationScoped
public class TimesheetRepository implements PanacheRepository<Timesheet> {

    // Somma le ore di un dipendente in un intervallo di date.
    //  il cuore della risposta dell'AI a "quante ore ha fatto X a agosto?"
    public BigDecimal sommaOrePeriodo(Long dipendenteId, LocalDate da, LocalDate a) {
        return find("dipendente.id = ?1 and dataLavoro between ?2 and ?3",
                dipendenteId, da, a)
                .stream()
                .map(t -> t.oreLavorate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}