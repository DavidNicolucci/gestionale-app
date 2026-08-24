package com.gestionale.dominio.ai.tools;

import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.model.enums.FiltroStato;
import com.gestionale.dominio.model.enums.StatoDipendente;
import com.gestionale.dominio.repository.DipendenteRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Cosa l'assistente puo' sapere sui dipendenti.
 *
 * Le descrizioni dei @Tool sono la parte che conta davvero: sono l'unica cosa
 * che il modello legge per decidere quale strumento usare e con quali parametri.
 * Se due strumenti si somigliano, va scritto nella descrizione quando preferire
 * l'uno o l'altro.
 *
 * Regola sugli elenchi: di default si mostrano solo i dipendenti utilizzabili oggi.
 * Scaduti ed eliminati compaiono solo se l'utente li chiede. Il filtro pero' lo decide
 * il modello leggendo la domanda, e potrebbe sbagliarsi: per questo ogni elenco filtrato
 * dichiara in fondo quanti ne ha lasciati fuori. Anche se il parametro arriva sbagliato,
 * l'informazione che esistono non si perde e l'assistente puo' correggersi.
 *
 * La ricerca del singolo (dettagliDipendente) non filtra niente: "quando e' scaduto il
 * contratto di Rossi" e' una domanda che ha senso proprio quando Rossi e' scaduto.
 *
 * Sono tutte letture: l'assistente non modifica niente.
 */
@ApplicationScoped
public class DipendenteTools {

    @Inject
    DipendenteRepository dipendenti;

    @Tool("""
            Elenca i dipendenti registrati in anagrafica, con nome, cognome e codice fiscale.
            Usalo quando l'utente chiede chi sono i dipendenti, quanti sono, oppure vuole l'elenco dei nominativi.
            Non usarlo per cercare una persona specifica: per quello c'e' cercaDipendenti.
            """)
    public String elencaDipendenti(
            @P("true solo se l'utente chiede espressamente anche i dipendenti non piu' in servizio "
                    + "(cessati, scaduti, eliminati, 'tutti quanti', 'compresi quelli usciti'). "
                    + "In tutti gli altri casi false: si elencano solo quelli attivi.")
            boolean includiNonAttivi) {

        FiltroStato filtro = includiNonAttivi ? FiltroStato.TUTTI : FiltroStato.SOLO_ATTIVI;

        List<Dipendente> trovati = dipendenti.elenco(filtro)
                .range(0, TestoTools.MAX_RIGHE - 1)
                .list();

        // Il totale lo chiediamo al database e non alla lista: la lista e' tagliata a
        // MAX_RIGHE, e dire "sono 50" quando sono 300 sarebbe una bugia.
        String intestazione = includiNonAttivi
                ? "Dipendenti in anagrafica, compresi scaduti ed eliminati: " + dipendenti.conta(filtro) + ".\n"
                : "Dipendenti attivi: " + dipendenti.conta(filtro) + ".\n";

        return intestazione
                + TestoTools.elenco(trovati, this::riga, "Nessun dipendente in anagrafica.")
                + (includiNonAttivi ? "" : codaNonElencati());
    }

    @Tool("""
            Cerca i dipendenti il cui nome, cognome o codice fiscale contiene il testo indicato.
            Usalo quando l'utente nomina una persona in modo parziale o incerto, per esempio
            "cerca Rossi", "chi si chiama Mario", "il dipendente con codice fiscale che inizia per RSS".
            """)
    public String cercaDipendenti(
            String testo,
            @P("true solo se l'utente chiede espressamente anche i dipendenti non piu' in servizio "
                    + "(cessati, scaduti, eliminati). In tutti gli altri casi false.")
            boolean includiNonAttivi) {

        // Cerchiamo sempre fra tutti e scremiamo qui: cosi' sappiamo esattamente quanti
        // ne abbiamo nascosti per questa ricerca, invece di doverla rifare una seconda
        // volta con un filtro diverso solo per contarli.
        List<Dipendente> trovati = dipendenti.cercaTestuale(testo, TestoTools.MAX_RIGHE, FiltroStato.TUTTI);

        if (includiNonAttivi) {
            return TestoTools.elenco(trovati, this::riga, "Nessun dipendente trovato per '" + testo + "'.");
        }

        LocalDate oggi = LocalDate.now();
        List<Dipendente> attivi = trovati.stream()
                .filter(d -> d.sottoContrattoIl(oggi))
                .toList();
        long nascosti = trovati.size() - attivi.size();

        String coda = nascosti == 0 ? "" : "\nAltri " + nascosti
                + " risultati non elencati perche' scaduti o eliminati:"
                + " richiama cercaDipendenti con includiNonAttivi=true per vederli.";

        return TestoTools.elenco(attivi, this::riga,
                "Nessun dipendente attivo trovato per '" + testo + "'.") + coda;
    }

    @Tool("""
            Restituisce la scheda completa di un dipendente: stato, codice fiscale, data di nascita,
            nazionalita', tipo di contratto, data di assunzione e data di scadenza del contratto.
            Il parametro e' il nome e cognome della persona.
            Usalo quando l'utente chiede i dati anagrafici o contrattuali di qualcuno,
            per esempio "che contratto ha Mario Rossi" o "quando e' stato assunto".
            Risponde anche sui dipendenti scaduti o eliminati.
            """)
    public String dettagliDipendente(String nomeCompleto) {
        Optional<Dipendente> trovato = dipendenti.perNominativo(nomeCompleto);

        if (trovato.isEmpty()) {
            // Frase e non eccezione: la legge il modello, che cosi' risponde
            // "non l'ho trovato" invece di inventarsi una scheda.
            return "Nessun dipendente trovato con nome '" + nomeCompleto
                    + "'. Il nome potrebbe essere incompleto o riferirsi a piu' persone: prova cercaDipendenti.";
        }

        Dipendente d = trovato.get();
        return """
                %s %s
                - Stato: %s
                - Codice fiscale: %s
                - Data di nascita: %s
                - Nazionalita': %s
                - Tipo di contratto: %s
                - Assunto il: %s
                - Scadenza contratto: %s
                """.formatted(
                d.nome, d.cognome,
                descrizioneStato(d),
                d.codiceFiscale,
                TestoTools.data(d.dataNascita),
                d.nazionalita,
                d.tipoContratto,
                TestoTools.data(d.dataAssunzione),
                d.dataScadenza == null ? "nessuna (tempo indeterminato)" : TestoTools.data(d.dataScadenza));
    }

    @Tool("""
            Elenca i dipendenti ancora in servizio il cui contratto a termine scade entro la data
            indicata, nel formato AAAA-MM-GG. I contratti a tempo indeterminato non compaiono
            perche' non scadono, e nemmeno quelli gia' scaduti: per quelli c'e' contrattiScaduti.
            Usalo per domande su contratti in scadenza, rinnovi da fare o su chi ha il contratto
            che sta per finire.
            """)
    public String contrattiInScadenza(LocalDate entro) {
        List<Dipendente> trovati = dipendenti.inScadenza(entro)
                .range(0, TestoTools.MAX_RIGHE - 1)
                .list();

        return TestoTools.elenco(trovati,
                d -> d.nome + " " + d.cognome + " - contratto " + d.tipoContratto
                        + " in scadenza il " + TestoTools.data(d.dataScadenza),
                "Nessun contratto in scadenza entro il " + TestoTools.data(entro) + ".");
    }

    @Tool("""
            Elenca i dipendenti con il contratto gia' finito, dal piu' recente. Non si possono
            piu' usare: per rimetterli in servizio serve un rinnovo del contratto.
            Usalo per domande tipo "chi ha il contratto scaduto", "chi non e' piu' in servizio",
            "a chi devo rinnovare il contratto".
            """)
    public String contrattiScaduti() {
        List<Dipendente> trovati = dipendenti.giaScaduti()
                .range(0, TestoTools.MAX_RIGHE - 1)
                .list();

        return TestoTools.elenco(trovati,
                d -> d.nome + " " + d.cognome + " - contratto " + d.tipoContratto
                        + " scaduto il " + TestoTools.data(d.dataScadenza),
                "Nessun dipendente con il contratto scaduto.");
    }

    // ---- Formattazione ----

    // Nome, cognome, codice fiscale, e lo stato solo quando non e' quello normale:
    // ripetere "attivo" su ogni riga costerebbe token senza dire niente, mentre uno
    // scaduto in mezzo a un elenco va segnalato o il modello lo presenta come gli altri.
    private String riga(Dipendente d) {
        String base = d.nome + " " + d.cognome + " (CF: " + d.codiceFiscale + ")";
        StatoDipendente stato = d.stato(LocalDate.now());

        return stato == StatoDipendente.ATTIVO ? base : base + " - " + descrizioneStato(d);
    }

    private String descrizioneStato(Dipendente d) {
        return switch (d.stato(LocalDate.now())) {
            case ATTIVO -> "in servizio";
            case IN_SCADENZA -> "in servizio, contratto in scadenza il " + TestoTools.data(d.dataScadenza);
            case SCADUTO -> "NON utilizzabile: contratto scaduto il " + TestoTools.data(d.dataScadenza);
            case ELIMINATO -> "NON utilizzabile: eliminato dall'anagrafica";
        };
    }

    // Riga finale degli elenchi filtrati: dice quanti ne restano fuori e come chiederli.
    // E' la rete di sicurezza sul parametro includiNonAttivi: se il modello lo mette a
    // false su una domanda che voleva tutti, l'utente vede comunque che ce ne sono altri.
    private String codaNonElencati() {
        long attivi = dipendenti.conta(FiltroStato.SOLO_ATTIVI);
        long nonEliminati = dipendenti.conta(FiltroStato.ESCLUDI_ELIMINATI);
        long tutti = dipendenti.conta(FiltroStato.TUTTI);

        long scaduti = nonEliminati - attivi;
        long eliminati = tutti - nonEliminati;

        if (scaduti == 0 && eliminati == 0) {
            return "";
        }

        StringBuilder coda = new StringBuilder("\nNon elencati: ");
        if (scaduti > 0) {
            coda.append(scaduti).append(" con il contratto scaduto");
        }
        if (scaduti > 0 && eliminati > 0) {
            coda.append(", ");
        }
        if (eliminati > 0) {
            coda.append(eliminati).append(eliminati == 1 ? " eliminato" : " eliminati");
        }
        coda.append(". Richiama elencaDipendenti con includiNonAttivi=true se l'utente li vuole.");

        return coda.toString();
    }
}
