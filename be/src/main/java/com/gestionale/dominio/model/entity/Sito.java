package com.gestionale.dominio.model.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "sito")
public class Sito extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "nome", nullable = false)
    public String nome;

    @Column(name = "indirizzo")
    public String indirizzo;

    // Molti siti appartengono a un cliente. LAZY: il cliente viene letto dal database
    // solo se qualcuno lo usa davvero.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)   // colonna cliente_id nella tabella sito
    public Cliente cliente;
}