package com.gestionale.dominio.model.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "cliente")
public class Cliente extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "ragione_sociale", nullable = false)
    public String ragioneSociale;

    @Column(name = "indirizzo")
    public String indirizzo;

    @Column(name = "partita_iva")
    public String partitaIva;
}