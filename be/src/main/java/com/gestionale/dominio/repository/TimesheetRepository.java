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

    // Ore di un dipendente in un periodo, insieme al primo e all'ultimo giorno in
    // cui ha lavorato davvero. La somma la fa il database: caricare migliaia di
    // righe in Java per ottenere un solo numero sarebbe uno spreco.
    //
    // SUM, MIN e MAX stanno nella stessa query e non in tre: sono aggregati sulle
    // stesse righe, e il database le scorre una volta sola.
    public OrePeriodo oreLavoratePeriodo(Long dipendenteId, LocalDate da, LocalDate a) {
        // Senza righe nel periodo l'aggregazione torna comunque una riga, con i tre
        // campi a null: chi chiama se ne accorge da OrePeriodo.vuoto().
        return getEntityManager()
                .createQuery("""
                        SELECT new com.gestionale.dominio.repository.OrePeriodo(
                            SUM(t.oreLavorate), MIN(t.dataLavoro), MAX(t.dataLavoro))
                        FROM Timesheet t
                        WHERE t.dipendente.id = ?1 AND t.dataLavoro BETWEEN ?2 AND ?3
                        """, OrePeriodo.class)
                .setParameter(1, dipendenteId)
                .setParameter(2, da)
                .setParameter(3, a)
                .getSingleResult();
    }

    // Ore su un singolo sito. Stessa logica di oreLavoratePeriodo: somma il database.
    public BigDecimal sommaOreSito(Long sitoId, LocalDate da, LocalDate a) {
        return sommaOZero("""
                SELECT SUM(t.oreLavorate) FROM Timesheet t
                WHERE t.sito.id = ?1 AND t.dataLavoro BETWEEN ?2 AND ?3
                """, sitoId, da, a);
    }

    // Ore su tutti i siti di un cliente: il salto sito -> cliente lo fa la query,
    // senza caricare prima l'elenco dei siti.
    public BigDecimal sommaOreCliente(Long clienteId, LocalDate da, LocalDate a) {
        return sommaOZero("""
                SELECT SUM(t.oreLavorate) FROM Timesheet t
                WHERE t.sito.cliente.id = ?1 AND t.dataLavoro BETWEEN ?2 AND ?3
                """, clienteId, da, a);
    }

    // Chi ha lavorato su un sito e per quante ore, dal piu' presente al meno.
    public List<RiepilogoOre> orePerDipendenteSuSito(Long sitoId, LocalDate da, LocalDate a) {
        return raggruppa("""
                SELECT new com.gestionale.dominio.repository.RiepilogoOre(
                    concat(t.dipendente.nome, ' ', t.dipendente.cognome), SUM(t.oreLavorate))
                FROM Timesheet t
                WHERE t.sito.id = ?1 AND t.dataLavoro BETWEEN ?2 AND ?3
                GROUP BY t.dipendente.nome, t.dipendente.cognome
                ORDER BY SUM(t.oreLavorate) DESC
                """, sitoId, da, a);
    }

    // Totale ore per dipendente nel periodo: e' la risposta a "chi ha lavorato di piu'".
    public List<RiepilogoOre> orePerDipendente(LocalDate da, LocalDate a) {
        return getEntityManager()
                .createQuery("""
                        SELECT new com.gestionale.dominio.repository.RiepilogoOre(
                            concat(t.dipendente.nome, ' ', t.dipendente.cognome), SUM(t.oreLavorate))
                        FROM Timesheet t
                        WHERE t.dataLavoro BETWEEN ?1 AND ?2
                        GROUP BY t.dipendente.nome, t.dipendente.cognome
                        ORDER BY SUM(t.oreLavorate) DESC
                        """, RiepilogoOre.class)
                .setParameter(1, da)
                .setParameter(2, a)
                .getResultList();
    }

    // Totale ore per cliente nel periodo, dal cliente con piu' ore.
    public List<RiepilogoOre> orePerCliente(LocalDate da, LocalDate a) {
        return getEntityManager()
                .createQuery("""
                        SELECT new com.gestionale.dominio.repository.RiepilogoOre(
                            t.sito.cliente.ragioneSociale, SUM(t.oreLavorate))
                        FROM Timesheet t
                        WHERE t.dataLavoro BETWEEN ?1 AND ?2
                        GROUP BY t.sito.cliente.ragioneSociale
                        ORDER BY SUM(t.oreLavorate) DESC
                        """, RiepilogoOre.class)
                .setParameter(1, da)
                .setParameter(2, a)
                .getResultList();
    }

    // Ultime registrazioni inserite, con dipendente e sito gia' caricati.
    public List<Timesheet> ultimeRegistrazioni(int quante) {
        return find("""
                SELECT t FROM Timesheet t JOIN FETCH t.dipendente JOIN FETCH t.sito
                ORDER BY t.dataLavoro DESC, t.id DESC
                """)
                .range(0, quante - 1)
                .list();
    }

    private BigDecimal sommaOZero(String jpql, Long id, LocalDate da, LocalDate a) {
        BigDecimal somma = getEntityManager()
                .createQuery(jpql, BigDecimal.class)
                .setParameter(1, id)
                .setParameter(2, da)
                .setParameter(3, a)
                .getSingleResult();

        return somma != null ? somma : BigDecimal.ZERO;
    }

    private List<RiepilogoOre> raggruppa(String jpql, Long id, LocalDate da, LocalDate a) {
        return getEntityManager()
                .createQuery(jpql, RiepilogoOre.class)
                .setParameter(1, id)
                .setParameter(2, da)
                .setParameter(3, a)
                .getResultList();
    }
}
