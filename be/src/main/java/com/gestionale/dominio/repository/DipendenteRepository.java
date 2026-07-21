package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.dto.DipendenteRicercaRequest;
import com.gestionale.dominio.model.entity.Dipendente;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped     // cosi' Quarkus lo puo' iniettare nei service
public class DipendenteRepository implements PanacheRepository<Dipendente> {

    // persist, findById, listAll, delete e count ce li da' gia' Panache.
    // Qui scriviamo solo le query nostre.

    // Query di ricerca con i filtri passati: quelli vuoti li salta.
    // Non impagina: ci pensa il service.
    public PanacheQuery<Dipendente> cerca(DipendenteRicercaRequest req, Sort sort) {
        FiltriPanache f = new FiltriPanache()
                .contiene("nome", req.nome)
                .contiene("cognome", req.cognome)
                .contiene("codiceFiscale", req.codiceFiscale);

        return find(f.where(), sort, f.params());
    }

    public List<Dipendente> cercaPerCognome(String cognome) {
        return list("cognome", cognome);   // Panache lo traduce in "WHERE cognome = ?1"
    }

    // Torna Optional e non null: cosi' chi lo chiama vede subito che il dipendente
    // potrebbe non esserci ed e' costretto a gestire il caso.
    public Optional<Dipendente> perCodiceFiscale(String cf) {
        return find("codiceFiscale", cf).firstResultOptional();
    }

    // Cerca per nome, cognome o nome completo. Filtra il database, non carichiamo
    // tutti i dipendenti per poi scremarli in Java.
    public Optional<Dipendente> perNominativo(String nominativo) {
        String cercato = nominativo.trim();
        List<Dipendente> risultati = find(
                "nome = ?1 or cognome = ?1 or concat(nome, ' ', cognome) = ?1", cercato)
                .range(0, 1)  // prende 2 righe: bastano per capire se il nome e' ambiguo
                .list();
        // Se ne troviamo piu' di uno non sappiamo quale sia: meglio niente che quello sbagliato.
        return risultati.size() == 1 ? Optional.of(risultati.get(0)) : Optional.empty();
    }
}
