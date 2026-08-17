package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.dto.ClienteRicercaRequest;
import com.gestionale.dominio.model.entity.Cliente;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped     // cosi' Quarkus lo puo' iniettare nei service
public class ClienteRepository implements PanacheRepository<Cliente> {

    // persist, findById, listAll, delete e count ce li da' gia' Panache.
    // Qui scriviamo solo le query nostre.

    // Query di ricerca con i filtri passati: quelli vuoti li salta.
    // Non impagina: ci pensa il service, cosi' risultati e conteggio usano gli stessi filtri.
    public PanacheQuery<Cliente> cerca(ClienteRicercaRequest req, Sort sort) {
        FiltriPanache f = new FiltriPanache()
                .contiene("ragioneSociale", req.ragioneSociale)
                .contiene("partitaIva", req.partitaIva);

        return find(f.where(), sort, f.params());
    }

    // Ricerca libera usata dall'assistente AI: l'utente scrive "acme" o un pezzo
    // di partita IVA, non il valore esatto. Il limite e' un paracadute: la
    // risposta finisce dentro al prompt del modello, e un elenco enorme costerebbe
    // in token senza servire a niente.
    public List<Cliente> cercaTestuale(String testo, int max) {
        String cercato = "%" + testo.trim().toLowerCase() + "%";
        return find("lower(ragioneSociale) like ?1 or lower(partitaIva) like ?1",
                Sort.by("ragioneSociale"), cercato)
                .range(0, max - 1)
                .list();
    }

    // Come perNominativo sui dipendenti: se il nome e' ambiguo meglio niente che
    // il cliente sbagliato, cosi' chi chiama puo' chiedere di essere piu' preciso.
    public Optional<Cliente> perRagioneSociale(String ragioneSociale) {
        String cercata = ragioneSociale.trim();
        List<Cliente> risultati = find("lower(ragioneSociale) = ?1", cercata.toLowerCase())
                .range(0, 1)
                .list();

        return risultati.size() == 1 ? Optional.of(risultati.get(0)) : Optional.empty();
    }
}