
package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.dto.SitoRicercaRequest;
import com.gestionale.dominio.model.entity.Sito;
import com.gestionale.dominio.model.enums.FiltroStato;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SitoRepository implements PanacheRepository<Sito> {

    // Come sugli altri repository: listAll() e count() di Panache non filtrano gli
    // eliminati, per quello che finisce a video usare i metodi qui sotto.

    private static final Sort PER_NOME = Sort.by("nome");

    // ---- Siti di un cliente ----

    public List<Sito> perCliente(Long clienteId, FiltroStato stato) {
        FiltriPanache f = new FiltriPanache()
                .uguale("cliente.id", clienteId)
                .nonEliminati(stato);
        return find(f.where(), PER_NOME, f.params()).list();
    }

    public List<Sito> perCliente(Long clienteId) {
        return perCliente(clienteId, FiltroStato.ESCLUDI_ELIMINATI);
    }

    // Quanti siti attivi ha un cliente. E' il controllo che blocca l'eliminazione del
    // cliente: conta sul database, non carica le righe per poi contarle in Java.
    public long contaAttiviPerCliente(Long clienteId) {
        return count("cliente.id = ?1 and eliminato = false", clienteId);
    }

    // I nomi dei siti attivi, per scriverli nel messaggio di errore. Legge solo la
    // colonna del nome e si ferma a "max": il messaggio ne cita pochi, caricare
    // l'entity intera di tutti sarebbe sprecato.
    public List<String> nomiAttiviPerCliente(Long clienteId, int max) {
        return getEntityManager()
                .createQuery("""
                        SELECT s.nome FROM Sito s
                        WHERE s.cliente.id = ?1 AND s.eliminato = false
                        ORDER BY s.nome
                        """, String.class)
                .setParameter(1, clienteId)
                .setMaxResults(max)
                .getResultList();
    }

    // ---- Elenchi e ricerche ----

    public PanacheQuery<Sito> elenco(FiltroStato stato, Sort sort) {
        FiltriPanache f = new FiltriPanache().nonEliminati(stato);
        return find(f.where(), sort, f.params());
    }

    public PanacheQuery<Sito> elenco(FiltroStato stato) {
        return elenco(stato, PER_NOME);
    }

    // Query di ricerca con i filtri passati: quelli vuoti li salta.
    // "cliente.id" filtra i siti di un cliente. Non impagina: ci pensa il service.
    public PanacheQuery<Sito> cerca(SitoRicercaRequest req, Sort sort) {
        FiltriPanache f = new FiltriPanache()
                .contiene("nome", req.nome)
                .uguale("cliente.id", req.clienteId)
                .nonEliminati(req.includiEliminati ? FiltroStato.TUTTI : FiltroStato.ESCLUDI_ELIMINATI);

        return find(f.where(), sort, f.params());
    }

    public long conta(FiltroStato stato) {
        return elenco(stato).count();
    }

    // Torna Optional perche' il sito potrebbe non esserci (es. nome sbagliato nell'Excel).
    //
    // Il nome del sito non e' unico sul database, e con la cancellazione logica non lo
    // e' davvero neanche fra i vivi: si puo' eliminare "Cantiere Via Roma" e riaprirne
    // uno con lo stesso nome l'anno dopo. Senza un ordinamento il database e' libero di
    // dare prima l'una o l'altra riga, e l'import attaccherebbe le ore al cantiere
    // sbagliato a seconda del piano di esecuzione. Ordiniamo per eliminato e poi per id:
    // prima il sito ancora aperto, e a parita' di stato il piu' recente.
    public Optional<Sito> perNome(String nome, FiltroStato stato) {
        FiltriPanache f = new FiltriPanache()
                .uguale("nome", nome)
                .nonEliminati(stato);

        return find(f.where(), Sort.by("eliminato").and("id", Sort.Direction.Descending), f.params())
                .firstResultOptional();
    }

    public Optional<Sito> perNome(String nome) {
        return perNome(nome, FiltroStato.ESCLUDI_ELIMINATI);
    }

    // Ricerca libera per l'assistente AI: una parte del nome o dell'indirizzo.
    // JOIN FETCH sul cliente perche' la risposta lo cita sempre: senza, sarebbe
    // una query in piu' per ogni sito trovato.
    public List<Sito> cercaTestuale(String testo, int max, FiltroStato stato) {
        String cercato = "%" + testo.trim().toLowerCase() + "%";

        // Ogni campo va qualificato con "s.": il JOIN FETCH porta dentro anche Cliente,
        // che ha una colonna 'eliminato' pure lui, e un "eliminato" scritto da solo
        // farebbe rifiutare la query come ambiguo.
        FiltriPanache f = new FiltriPanache()
                .nonEliminati(stato, "s")
                .condizione("lower(s.nome) LIKE :testo OR lower(s.indirizzo) LIKE :testo")
                .parametro("testo", cercato);

        // Il JOIN FETCH va scritto per esteso, quindi la WHERE costruita da FiltriPanache
        // la incolliamo dentro la query: i nomi dei campi li scrive questo metodo, dal
        // client arriva solo il valore di :testo, che resta un parametro.
        return find("SELECT s FROM Sito s JOIN FETCH s.cliente WHERE " + f.where() + " ORDER BY s.nome",
                f.params())
                .range(0, max - 1)
                .list();
    }

    public List<Sito> cercaTestuale(String testo, int max) {
        return cercaTestuale(testo, max, FiltroStato.ESCLUDI_ELIMINATI);
    }

    // Elenco completo, con il cliente gia' caricato per lo stesso motivo.
    public List<Sito> elencoConCliente(int max, FiltroStato stato) {
        FiltriPanache f = new FiltriPanache().nonEliminati(stato, "s");

        return find("SELECT s FROM Sito s JOIN FETCH s.cliente WHERE " + f.where() + " ORDER BY s.nome",
                f.params())
                .range(0, max - 1)
                .list();
    }

    public List<Sito> elencoConCliente(int max) {
        return elencoConCliente(max, FiltroStato.ESCLUDI_ELIMINATI);
    }
}
