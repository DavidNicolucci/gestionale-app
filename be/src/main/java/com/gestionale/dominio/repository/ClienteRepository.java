package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.dto.ClienteRicercaRequest;
import com.gestionale.dominio.model.entity.Cliente;
import com.gestionale.dominio.model.enums.FiltroStato;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@ApplicationScoped     // cosi' Quarkus lo puo' iniettare nei service
public class ClienteRepository implements PanacheRepository<Cliente> {

    // persist, findById, listAll, delete e count ce li da' gia' Panache.
    // Qui scriviamo solo le query nostre.
    //
    // Da quando la cancellazione e' logica, listAll() e count() di Panache tirano su
    // anche gli eliminati: per quello che finisce a video non usarli direttamente,
    // passare dai metodi qui sotto, che il filtro ce l'hanno.

    private static final Sort PER_RAGIONE_SOCIALE = Sort.by("ragioneSociale");

    // ---- Elenchi e ricerche ----

    public PanacheQuery<Cliente> elenco(FiltroStato stato, Sort sort) {
        FiltriPanache f = new FiltriPanache().nonEliminati(stato);
        return find(f.where(), sort, f.params());
    }

    public PanacheQuery<Cliente> elenco(FiltroStato stato) {
        return elenco(stato, PER_RAGIONE_SOCIALE);
    }

    public long conta(FiltroStato stato) {
        return elenco(stato).count();
    }

    // Query di ricerca con i filtri passati: quelli vuoti li salta.
    // Non impagina: ci pensa il service, cosi' risultati e conteggio usano gli stessi filtri.
    //
    // Gli eliminati entrano solo se la richiesta li chiede: e' la spunta
    // "mostra eliminati" della barra filtri, come sui dipendenti.
    public PanacheQuery<Cliente> cerca(ClienteRicercaRequest req, Sort sort) {
        FiltriPanache f = new FiltriPanache()
                .contiene("ragioneSociale", req.ragioneSociale)
                .contiene("partitaIva", req.partitaIva)
                .nonEliminati(req.includiEliminati ? FiltroStato.TUTTI : FiltroStato.ESCLUDI_ELIMINATI);

        return find(f.where(), sort, f.params());
    }

    // Ricerca libera usata dall'assistente AI: l'utente scrive "acme" o un pezzo
    // di partita IVA, non il valore esatto. Il limite e' un paracadute: la
    // risposta finisce dentro al prompt del modello, e un elenco enorme costerebbe
    // in token senza servire a niente.
    public List<Cliente> cercaTestuale(String testo, int max, FiltroStato stato) {
        String cercato = "%" + testo.trim().toLowerCase() + "%";

        FiltriPanache f = new FiltriPanache()
                .nonEliminati(stato)
                .condizione("lower(ragioneSociale) like :testo or lower(partitaIva) like :testo")
                .parametro("testo", cercato);

        return find(f.where(), PER_RAGIONE_SOCIALE, f.params())
                .range(0, max - 1)
                .list();
    }

    // Senza indicazioni si cercano solo i clienti ancora in anagrafica.
    public List<Cliente> cercaTestuale(String testo, int max) {
        return cercaTestuale(testo, max, FiltroStato.ESCLUDI_ELIMINATI);
    }

    // ---- Ricerca del singolo cliente ----

    // Come perNominativo sui dipendenti: se il nome e' ambiguo meglio niente che
    // il cliente sbagliato, cosi' chi chiama puo' chiedere di essere piu' preciso.
    //
    // Qui non filtriamo gli eliminati, per la stessa ragione di perNominativo: questo
    // metodo risolve un nome, non compila un elenco. "Quante ore abbiamo fatto per
    // Acme l'anno scorso" e' una domanda legittima anche dopo che Acme e' stata
    // eliminata, e le sue ore sono ancora li'. Chi mostra il risultato dice che e'
    // eliminato, non fa finta che non esista.
    public Optional<Cliente> perRagioneSociale(String ragioneSociale) {
        String cercata = ragioneSociale.trim();
        List<Cliente> risultati = find("lower(ragioneSociale) = ?1", cercata.toLowerCase())
                .range(0, 1)
                .list();

        return risultati.size() == 1 ? Optional.of(risultati.get(0)) : Optional.empty();
    }

    // Legge il cliente prendendo il lucchetto sulla riga: da qui fino alla fine della
    // transazione nessun altro la puo' toccare. Serve al controllo "ha ancora siti
    // attivi?" prima di eliminarlo, altrimenti fra il controllo e l'UPDATE ci sta
    // un'altra transazione che gli attacca un sito nuovo.
    public Optional<Cliente> perIdBloccato(Long id) {
        return find("id", id)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }

    // L'altra meta' della stessa protezione, dal lato di chi aggancia un sito al
    // cliente: tiene la riga ferma fino a fine transazione senza impedire ad altri di
    // leggerla. Chi vuole eliminare il cliente chiede il lucchetto esclusivo e aspetta
    // il nostro turno, quindi i due gesti non si sovrappongono mai.
    public Optional<Cliente> perIdCondiviso(Long id) {
        return find("id", id)
                .withLock(LockModeType.PESSIMISTIC_READ)
                .firstResultOptional();
    }
}
