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

    @ManyToOne(fetch = FetchType.LAZY)        // molti siti -> un cliente
    @JoinColumn(name = "cliente_id", nullable = false)   // la FK in tabella sito
    public Cliente cliente;
}