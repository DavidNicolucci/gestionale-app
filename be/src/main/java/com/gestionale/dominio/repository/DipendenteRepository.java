package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.dto.DipendenteRicercaRequest;
import com.gestionale.dominio.model.entity.Dipendente;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
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

    // Ricerca libera per l'assistente AI: una parte del nome, del cognome o del
    // codice fiscale. perNominativo pretende il valore esatto, questa no: serve
    // quando l'utente scrive "i Rossi" o ricorda il nome a meta'.
    public List<Dipendente> cercaTestuale(String testo, int max) {
        String cercato = "%" + testo.trim().toLowerCase() + "%";
        return find("""
                lower(nome) LIKE ?1 OR lower(cognome) LIKE ?1
                OR lower(concat(nome, ' ', cognome)) LIKE ?1
                OR lower(codiceFiscale) LIKE ?1
                """, Sort.by("cognome").and("nome"), cercato)
                .range(0, max - 1)
                .list();
    }

    // Solo i contratti a termine: quelli indeterminati hanno dataScadenza nulla
    // e non scadono, quindi non devono comparire tra le scadenze.
    public List<Dipendente> contrattiInScadenza(LocalDate entro) {
        return find("dataScadenza IS NOT NULL AND dataScadenza <= ?1",
                Sort.by("dataScadenza"), entro)
                .list();
    }
}
