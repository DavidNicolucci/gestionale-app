package com.gestionale.dominio.model.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

// @BatchSize dice a Hibernate di caricare i clienti a gruppi di 50 invece che uno per
// volta. Serve nella lista dei siti: ogni sito mostra il nome del suo cliente, e senza
// questa riga una pagina da 100 siti farebbe 100 query in piu'.
@Entity
@Table(name = "cliente")
@BatchSize(size = 50)
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

    // Cancellazione logica. Un cliente eliminato sparisce da elenchi, tendine e
    // assistente, ma le ore lavorate sui suoi siti restano e continuano a contare
    // nei totali: sono fatti gia' avvenuti, spesso gia' fatturati.
    @Column(name = "eliminato", nullable = false)
    public boolean eliminato;
}