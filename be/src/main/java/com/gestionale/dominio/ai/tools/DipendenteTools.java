package com.gestionale.dominio.ai.tools;

import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.repository.DipendenteRepository;
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
 * Sono tutte letture: l'assistente non modifica niente.
 */
@ApplicationScoped
public class DipendenteTools {

    @Inject
    DipendenteRepository dipendenti;

    @Tool("""
            Elenca tutti i dipendenti registrati in anagrafica, con nome, cognome e codice fiscale.
            Usalo quando l'utente chiede chi sono i dipendenti, quanti sono, oppure vuole l'elenco dei nominativi.
            Non usarlo per cercare una persona specifica: per quello c'e' cercaDipendenti.
            """)
    public String elencaDipendenti() {
        List<Dipendente> trovati = dipendenti.findAll().range(0, TestoTools.MAX_RIGHE - 1).list();

        return "Dipendenti registrati: " + dipendenti.count() + ".\n"
                + TestoTools.elenco(trovati, this::riga, "Nessun dipendente in anagrafica.");
    }

    @Tool("""
            Cerca i dipendenti il cui nome, cognome o codice fiscale contiene il testo indicato.
            Usalo quando l'utente nomina una persona in modo parziale o incerto, per esempio
            "cerca Rossi", "chi si chiama Mario", "il dipendente con codice fiscale che inizia per RSS".
            """)
    public String cercaDipendenti(String testo) {
        List<Dipendente> trovati = dipendenti.cercaTestuale(testo, TestoTools.MAX_RIGHE);

        return TestoTools.elenco(trovati, this::riga,
                "Nessun dipendente trovato per '" + testo + "'.");
    }

    @Tool("""
            Restituisce la scheda completa di un dipendente: codice fiscale, data di nascita,
            nazionalita', tipo di contratto, data di assunzione e data di scadenza del contratto.
            Il parametro e' il nome e cognome della persona.
            Usalo quando l'utente chiede i dati anagrafici o contrattuali di qualcuno,
            per esempio "che contratto ha Mario Rossi" o "quando e' stato assunto".
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
                - Codice fiscale: %s
                - Data di nascita: %s
                - Nazionalita': %s
                - Tipo di contratto: %s
                - Assunto il: %s
                - Scadenza contratto: %s
                """.formatted(
                d.nome, d.cognome,
                d.codiceFiscale,
                TestoTools.data(d.dataNascita),
                d.nazionalita,
                d.tipoContratto,
                TestoTools.data(d.dataAssunzione),
                d.dataScadenza == null ? "nessuna (tempo indeterminato)" : TestoTools.data(d.dataScadenza));
    }

    @Tool("""
            Elenca i dipendenti il cui contratto a termine scade entro la data indicata,
            nel formato AAAA-MM-GG. I contratti a tempo indeterminato non compaiono perche' non scadono.
            Usalo per domande su contratti in scadenza, rinnovi o su chi ha il contratto che finisce.
            """)
    public String contrattiInScadenza(LocalDate entro) {
        List<Dipendente> trovati = dipendenti.contrattiInScadenza(entro);

        return TestoTools.elenco(trovati,
                d -> d.nome + " " + d.cognome + " - contratto " + d.tipoContratto
                        + " in scadenza il " + TestoTools.data(d.dataScadenza),
                "Nessun contratto in scadenza entro il " + TestoTools.data(entro) + ".");
    }

    private String riga(Dipendente d) {
        return d.nome + " " + d.cognome + " (CF: " + d.codiceFiscale + ")";
    }
}
