package com.gestionale.dominio.model.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "timesheet")
public class Timesheet extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dipendente_id", nullable = false)
    public Dipendente dipendente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sito_id", nullable = false)
    public Sito sito;

    @Column(name = "data_lavoro", nullable = false)
    public LocalDate dataLavoro;

    @Column(name = "ore_lavorate", nullable = false)
    public BigDecimal oreLavorate;     // BigDecimal <-> DECIMAL(5,2): precisione esatta

    @Column(name = "note")
    public String note;
}