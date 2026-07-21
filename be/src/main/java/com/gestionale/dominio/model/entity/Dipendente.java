package com.gestionale.dominio.model.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "dipendente")
public class Dipendente extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // l'id lo assegna il database
    public Long id;

    @Column(name = "nome", nullable = false)
    public String nome;

    @Column(name = "cognome", nullable = false)
    public String cognome;

    @Column(name = "codice_fiscale", nullable = false)
    public String codiceFiscale;

    @Column(name = "data_nascita", nullable = false)
    public LocalDate dataNascita;

    @Column(name = "nazionalita", nullable = false)
    public String nazionalita;

    @Column(name = "tipo_contratto", nullable = false)
    public String tipoContratto;

    @Column(name = "data_assunzione", nullable = false)
    public LocalDate dataAssunzione;

    @Column(name = "data_scadenza")
    public LocalDate dataScadenza;     // vuota se il contratto e' a tempo indeterminato
}