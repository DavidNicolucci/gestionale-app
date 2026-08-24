package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.dto.DipendenteRicercaRequest;
import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.model.enums.FiltroStato;
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
    //
    // Da quando la cancellazione e' logica, listAll() e count() di Panache tirano su
    // anche gli eliminati: per quello che finisce a video non usarli direttamente,
    // passare dai metodi qui sotto, che il filtro ce l'hanno.

    // Ordinamento buono per gli elenchi mostrati a video e all'assistente.
    private static final Sort PER_NOMINATIVO = Sort.by("cognome").and("nome");

    // ---- Elenchi e ricerche ----

    // Elenco filtrato per stato. Torna la query e non la lista: chi chiama decide
    // se contarla, impaginarla o prenderne solo le prime righe.
    public PanacheQuery<Dipendente> elenco(FiltroStato stato, Sort sort) {
        FiltriPanache f = perStato(new FiltriPanache(), stato);
        return find(f.where(), sort, f.params());
    }

    public PanacheQuery<Dipendente> elenco(FiltroStato stato) {
        return elenco(stato, PER_NOMINATIVO);
    }

    public long conta(FiltroStato stato) {
        return elenco(stato).count();
    }

    // Query di ricerca con i filtri passati: quelli vuoti li salta.
    // Non impagina: ci pensa il service.
    //
    // Gli scaduti ci sono sempre, perche' la tabella li mostra in grigio. Gli eliminati
    // solo se la richiesta li chiede: e' la spunta "mostra eliminati" della barra filtri.
    public PanacheQuery<Dipendente> cerca(DipendenteRicercaRequest req, Sort sort) {
        FiltriPanache f = new FiltriPanache()
                .contiene("nome", req.nome)
                .contiene("cognome", req.cognome)
                .contiene("codiceFiscale", req.codiceFiscale);

        perStato(f, req.includiEliminati ? FiltroStato.TUTTI : FiltroStato.ESCLUDI_ELIMINATI);

        return find(f.where(), sort, f.params());
    }

    public List<Dipendente> cercaPerCognome(String cognome) {
        return list("cognome", cognome);   // Panache lo traduce in "WHERE cognome = ?1"
    }

    // ---- Ricerca del singolo dipendente ----

    // Torna Optional e non null: cosi' chi lo chiama vede subito che il dipendente
    // potrebbe non esserci ed e' costretto a gestire il caso.
    // Il codice fiscale e' UNIQUE sul database, quindi la riga e' al massimo una anche
    // con FiltroStato.TUTTI: chi viene eliminato si tiene la sua riga e il suo codice.
    public Optional<Dipendente> perCodiceFiscale(String cf, FiltroStato stato) {
        FiltriPanache f = perStato(new FiltriPanache(), stato)
                .uguale("codiceFiscale", cf);
        return find(f.where(), f.params()).firstResultOptional();
    }

    // Chi cerca un codice fiscale senza dire altro vuole qualcuno con cui lavorare, non
    // un cessato: fuori gli eliminati. Gli scaduti restano, perche' un timesheet
    // arretrato sul loro vecchio contratto e' legittimo e a validarlo ci pensa
    // Dipendente.sottoContrattoIl() con la data del lavoro svolto.
    public Optional<Dipendente> perCodiceFiscale(String cf) {
        return perCodiceFiscale(cf, FiltroStato.ESCLUDI_ELIMINATI);
    }

    // Cerca per nome, cognome o nome completo. Filtra il database, non carichiamo
    // tutti i dipendenti per poi scremarli in Java.
    //
    // Qui non filtriamo per stato: questo metodo risponde a "che contratto ha Rossi",
    // e la domanda ha senso soprattutto quando Rossi e' scaduto. Filtrare un elenco
    // e' utile, filtrare la risoluzione di un nome vuol dire non poter piu' chiedere
    // di quella persona.
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
    public List<Dipendente> cercaTestuale(String testo, int max, FiltroStato stato) {
        String cercato = "%" + testo.trim().toLowerCase() + "%";

        FiltriPanache f = perStato(new FiltriPanache(), stato)
                .condizione("lower(nome) LIKE :testo OR lower(cognome) LIKE :testo"
                        + " OR lower(concat(nome, ' ', cognome)) LIKE :testo"
                        + " OR lower(codiceFiscale) LIKE :testo")
                .parametro("testo", cercato);

        return find(f.where(), PER_NOMINATIVO, f.params())
                .range(0, max - 1)
                .list();
    }

    // Senza indicazioni si cercano solo gli attivi: e' il caso normale.
    public List<Dipendente> cercaTestuale(String testo, int max) {
        return cercaTestuale(testo, max, FiltroStato.SOLO_ATTIVI);
    }

    // ---- Scadenze ----

    // Contratti a termine che finiscono da oggi fino alla data indicata: quelli ancora
    // validi ma che stanno per scadere, cioe' quelli su cui si puo' ancora agire.
    // Chi e' gia' scaduto non compare: non e' "in scadenza", e' scaduto, e ha la sua
    // query. Gli indeterminati hanno dataScadenza nulla e non scadono mai.
    public PanacheQuery<Dipendente> inScadenza(LocalDate entro) {
        FiltriPanache f = perStato(new FiltriPanache(), FiltroStato.ESCLUDI_ELIMINATI)
                .condizione("dataScadenza IS NOT NULL AND dataScadenza BETWEEN :oggi AND :entro")
                .parametro("oggi", oggi())
                .parametro("entro", entro);

        return find(f.where(), Sort.by("dataScadenza"), f.params());
    }

    // Contratti gia' finiti: sono i dipendenti in stato SCADUTO, quelli che per tornare
    // utilizzabili devono passare dal rinnovo. Ordinati dal piu' recente: chi e' scaduto
    // ieri interessa piu' di chi e' scaduto tre anni fa.
    public PanacheQuery<Dipendente> giaScaduti() {
        FiltriPanache f = perStato(new FiltriPanache(), FiltroStato.ESCLUDI_ELIMINATI)
                .condizione("dataScadenza IS NOT NULL AND dataScadenza < :oggi")
                .parametro("oggi", oggi());

        return find(f.where(), Sort.by("dataScadenza", Sort.Direction.Descending), f.params());
    }

    // ---- Interni ----

    // Traduce lo stato in condizioni sulla query. Il parametro :oggi lo aggiungiamo solo
    // dove la condizione lo usa davvero: passarne uno che nella query non compare fa
    // fallire Hibernate con "Could not locate named parameter".
    private FiltriPanache perStato(FiltriPanache f, FiltroStato stato) {
        return switch (stato) {
            case SOLO_ATTIVI -> f
                    .condizione("eliminato = false AND (dataScadenza IS NULL OR dataScadenza >= :oggi)")
                    .parametro("oggi", oggi());
            case ESCLUDI_ELIMINATI -> f.condizione("eliminato = false");
            case TUTTI -> f;
        };
    }

    // La data di oggi la legge il repository, non chi lo chiama: le firme restano
    // pulite. Le regole che devono restare verificabili a tavolino stanno sull'entity
    // (sottoContrattoIl, stato), e quelle la data se la fanno passare.
    private LocalDate oggi() {
        return LocalDate.now();
    }
}
