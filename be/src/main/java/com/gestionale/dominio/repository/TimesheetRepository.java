package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.entity.Timesheet;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TimesheetRepository implements PanacheRepository<Timesheet> {

    // REGOLA DELLE SOMME, da tenere presente prima di toccare le query qui sotto.
    //
    // Filtriamo SOLO "t.eliminato = false". Non filtriamo mai il dipendente, il sito o
    // il cliente eliminati, e non e' una dimenticanza: quelle ore sono state lavorate
    // davvero e quasi sempre gia' fatturate. Se aggiungessimo "AND t.sito.eliminato =
    // false", il giorno che si chiude un cantiere il suo fatturato sparirebbe dai
    // riepiloghi senza che nessuno abbia cancellato niente, cioe' esattamente il danno
    // che la cancellazione logica doveva evitare.
    //
    // L'unico flag che toglie ore dai totali e' quello sulla riga di timesheet, ed e'
    // il suo mestiere: annullare una registrazione sbagliata.

    private static final String NON_ELIMINATI = "t.eliminato = false";

    // Il DTO mostra nome del dipendente e nome del sito. Con un normale listAll()
    // Hibernate li leggerebbe uno per volta, cioe' 2 query in piu' per ogni riga.
    // Con JOIN FETCH li carica tutti insieme: una query sola.
    public List<Timesheet> listaConRelazioni() {
        return find("""
                SELECT t FROM Timesheet t JOIN FETCH t.dipendente JOIN FETCH t.sito
                WHERE t.eliminato = false
                ORDER BY t.dataLavoro DESC, t.id DESC
                """).list();
    }

    // Come sopra ma su una riga sola: evita le 2 query in piu'.
    // Torna Optional perche' l'id potrebbe non esistere: il service lo trasforma in 404.
    //
    // Qui gli eliminati ci sono: e' il dettaglio di una riga precisa, e serve anche a
    // ripristinarla. Nascondere la scheda di una riga che esiste vuol dire non poterla
    // piu' rimettere a posto. Nella risposta il flag c'e', chi la mostra lo dice.
    public Optional<Timesheet> perIdConRelazioni(Long id) {
        return find("SELECT t FROM Timesheet t JOIN FETCH t.dipendente JOIN FETCH t.sito WHERE t.id = ?1", id)
                .firstResultOptional();
    }

    // Ore di un dipendente in un periodo, insieme al primo e all'ultimo giorno in
    // cui ha lavorato davvero.
    public OrePeriodo oreLavoratePeriodo(Long dipendenteId, LocalDate da, LocalDate a) {
        return aggrega("t.dipendente.id = ?1", dipendenteId, da, a);
    }

    // Ore su un singolo sito, con il primo e l'ultimo giorno di attivita' del sito.
    public OrePeriodo oreSitoPeriodo(Long sitoId, LocalDate da, LocalDate a) {
        return aggrega("t.sito.id = ?1", sitoId, da, a);
    }

    // Ore su tutti i siti di un cliente: il salto sito -> cliente lo fa la query,
    // senza caricare prima l'elenco dei siti. Ci sono anche i siti eliminati del
    // cliente, vedi la regola delle somme in cima alla classe.
    public OrePeriodo oreClientePeriodo(Long clienteId, LocalDate da, LocalDate a) {
        return aggrega("t.sito.cliente.id = ?1", clienteId, da, a);
    }

    // Chi ha lavorato su un sito e per quante ore, dal piu' presente al meno.
    public List<RiepilogoOre> orePerDipendenteSuSito(Long sitoId, LocalDate da, LocalDate a) {
        return raggruppa("""
                SELECT new com.gestionale.dominio.repository.RiepilogoOre(
                    concat(t.dipendente.nome, ' ', t.dipendente.cognome), SUM(t.oreLavorate))
                FROM Timesheet t
                WHERE t.sito.id = ?1 AND t.eliminato = false AND t.dataLavoro BETWEEN ?2 AND ?3
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
                        WHERE t.eliminato = false AND t.dataLavoro BETWEEN ?1 AND ?2
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
                        WHERE t.eliminato = false AND t.dataLavoro BETWEEN ?1 AND ?2
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
                WHERE t.eliminato = false
                ORDER BY t.dataLavoro DESC, t.id DESC
                """)
                .range(0, quante - 1)
                .list();
    }

    // La riga attiva per quel dipendente, quel sito e quel giorno: e' la chiave naturale
    // del timesheet, quella su cui il database ha l'indice unico uq_timesheet_giorno.
    //
    // Serve a due cose che sono la stessa cosa vista da due lati: all'import, per
    // correggere le ore invece di aggiungere un doppione quando lo stesso file viene
    // caricato due volte; agli endpoint manuali, per rispondere 409 con una frase
    // comprensibile invece di lasciar arrivare la violazione del vincolo come un 500.
    //
    // Solo le righe attive: quelle annullate non contano piu' e non devono impedire di
    // registrare le ore giuste al posto di quelle sbagliate.
    public Optional<Timesheet> perGiornoLavorato(Long dipendenteId, Long sitoId, LocalDate giorno) {
        return find("dipendente.id = ?1 and sito.id = ?2 and dataLavoro = ?3 and eliminato = false",
                dipendenteId, sitoId, giorno)
                .firstResultOptional();
    }

    // Come sopra ma escludendo una riga: serve quando si CORREGGE una registrazione, che
    // altrimenti risulterebbe in conflitto con se stessa.
    public Optional<Timesheet> altroNelGiorno(Long dipendenteId, Long sitoId, LocalDate giorno, Long idDaEscludere) {
        return find("dipendente.id = ?1 and sito.id = ?2 and dataLavoro = ?3 and eliminato = false and id <> ?4",
                dipendenteId, sitoId, giorno, idDaEscludere)
                .firstResultOptional();
    }

    // Quante righe di ore sono appese a un sito, eliminate comprese. Serve a scriverlo
    // nel log quando il sito viene eliminato: e' il numero che dice quanta storia
    // sarebbe sparita con una cancellazione fisica.
    public long contaPerSito(Long sitoId) {
        return count("sito.id", sitoId);
    }

    // Le tre domande sulle ore in un periodo - dipendente, sito, cliente - cambiano
    // solo per a chi si riferiscono le righe, non per cosa se ne ricava: la parte
    // fissa sta qui una volta sola. Il frammento di WHERE e' scritto nei metodi qui
    // sopra, non arriva da fuori: nessun dato dell'utente entra nella query.
    //
    // La somma la fa il database: caricare migliaia di righe in Java per ottenere
    // tre valori sarebbe uno spreco. E SUM, MIN e MAX stanno nella stessa query e
    // non in tre, perche' sono aggregati sulle stesse righe: il database le scorre
    // una volta sola.
    private OrePeriodo aggrega(String condizione, Long id, LocalDate da, LocalDate a) {
        // Senza righe nel periodo l'aggregazione torna comunque una riga, con i tre
        // campi a null: chi chiama se ne accorge da OrePeriodo.vuoto().
        return getEntityManager()
                .createQuery("""
                        SELECT new com.gestionale.dominio.repository.OrePeriodo(
                            SUM(t.oreLavorate), MIN(t.dataLavoro), MAX(t.dataLavoro))
                        FROM Timesheet t
                        WHERE %s AND %s AND t.dataLavoro BETWEEN ?2 AND ?3
                        """.formatted(condizione, NON_ELIMINATI), OrePeriodo.class)
                .setParameter(1, id)
                .setParameter(2, da)
                .setParameter(3, a)
                .getSingleResult();
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
