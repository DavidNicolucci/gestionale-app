
package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.dto.SitoRicercaRequest;
import com.gestionale.dominio.model.entity.Sito;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SitoRepository implements PanacheRepository<Sito> {

    public List<Sito> perCliente(Long clienteId) {
        return list("cliente.id", clienteId);
    }

    // Query di ricerca con i filtri passati: quelli vuoti li salta.
    // "cliente.id" filtra i siti di un cliente. Non impagina: ci pensa il service.
    public PanacheQuery<Sito> cerca(SitoRicercaRequest req, Sort sort) {
        FiltriPanache f = new FiltriPanache()
                .contiene("nome", req.nome)
                .uguale("cliente.id", req.clienteId);

        return find(f.where(), sort, f.params());
    }

    // Torna Optional perche' il sito potrebbe non esserci (es. nome sbagliato nell'Excel).
    public Optional<Sito> perNome(String nome) {
        return find("nome", nome).firstResultOptional();
    }

    // Ricerca libera per l'assistente AI: una parte del nome o dell'indirizzo.
    // JOIN FETCH sul cliente perche' la risposta lo cita sempre: senza, sarebbe
    // una query in piu' per ogni sito trovato.
    public List<Sito> cercaTestuale(String testo, int max) {
        String cercato = "%" + testo.trim().toLowerCase() + "%";
        return find("""
                SELECT s FROM Sito s JOIN FETCH s.cliente
                WHERE lower(s.nome) LIKE ?1 OR lower(s.indirizzo) LIKE ?1
                ORDER BY s.nome
                """, cercato)
                .range(0, max - 1)
                .list();
    }

    // Elenco completo, con il cliente gia' caricato per lo stesso motivo.
    public List<Sito> elencoConCliente(int max) {
        return find("SELECT s FROM Sito s JOIN FETCH s.cliente ORDER BY s.nome")
                .range(0, max - 1)
                .list();
    }
}