package com.gestionale.dominio.ai.tools;

import com.gestionale.dominio.model.entity.Cliente;
import com.gestionale.dominio.model.entity.Sito;
import com.gestionale.dominio.model.enums.FiltroStato;
import com.gestionale.dominio.repository.ClienteRepository;
import com.gestionale.dominio.repository.SitoRepository;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

/** Cosa l'assistente puo' sapere sui clienti. Sono tutte letture. */
@ApplicationScoped
public class ClienteTools {

    @Inject
    ClienteRepository clienti;

    @Inject
    SitoRepository siti;

    @Tool("""
            Elenca i clienti registrati, con ragione sociale, partita IVA e indirizzo.
            Usalo quando l'utente chiede quali sono i clienti, quanti sono o vuole l'elenco delle aziende.
            Per trovare un cliente preciso usa invece cercaClienti.
            """)
    public String elencaClienti() {
        // Elenco e conteggio filtrano gli eliminati, e devono farlo tutti e due: un
        // "clienti registrati: 25" seguito da 22 righe manderebbe il modello a
        // inventarsi una spiegazione per i tre mancanti.
        List<Cliente> trovati = clienti.elenco(FiltroStato.ESCLUDI_ELIMINATI)
                .range(0, TestoTools.MAX_RIGHE - 1)
                .list();

        return "Clienti registrati: " + clienti.conta(FiltroStato.ESCLUDI_ELIMINATI) + ".\n"
                + TestoTools.elenco(trovati, this::riga, "Nessun cliente in anagrafica.");
    }

    @Tool("""
            Cerca i clienti la cui ragione sociale o partita IVA contiene il testo indicato.
            Usalo quando l'utente nomina un cliente in modo parziale, per esempio "cerca Acme",
            "il cliente che si chiama Rossi" o quando indica una partita IVA incompleta.
            """)
    public String cercaClienti(String testo) {
        List<Cliente> trovati = clienti.cercaTestuale(testo, TestoTools.MAX_RIGHE);

        return TestoTools.elenco(trovati, this::riga, "Nessun cliente trovato per '" + testo + "'.");
    }

    @Tool("""
            Restituisce la scheda di un cliente: partita IVA, indirizzo e l'elenco dei suoi siti
            (cantieri, negozi, sedi operative). Il parametro e' la ragione sociale del cliente.
            Usalo quando l'utente chiede i dati di un cliente o dove si lavora per quel cliente.
            """)
    public String dettagliCliente(String ragioneSociale) {
        Optional<Cliente> trovato = clienti.perRagioneSociale(ragioneSociale);

        if (trovato.isEmpty()) {
            return "Nessun cliente trovato con ragione sociale '" + ragioneSociale
                    + "'. Prova cercaClienti per cercarlo con un frammento del nome.";
        }

        Cliente c = trovato.get();
        List<Sito> suoiSiti = siti.perCliente(c.id);

        // La scheda risponde anche su un cliente eliminato, perche' le sue ore vecchie
        // esistono ancora e ha senso chiederne conto. Ma va detto: senza questa riga il
        // modello lo presenterebbe come un cliente qualsiasi.
        String avviso = c.eliminato
                ? "ATTENZIONE: questo cliente e' stato eliminato dall'anagrafica. Le ore lavorate"
                        + " per lui restano valide, ma non ci si possono collegare siti nuovi."
                        + System.lineSeparator()
                : "";

        return avviso + """
                %s
                - Partita IVA: %s
                - Indirizzo: %s
                - Siti collegati (%d):
                %s
                """.formatted(
                c.ragioneSociale,
                valore(c.partitaIva),
                valore(c.indirizzo),
                suoiSiti.size(),
                TestoTools.elenco(suoiSiti,
                        s -> s.nome + (s.indirizzo == null ? "" : " (" + s.indirizzo + ")"),
                        "  nessun sito collegato"));
    }

    private String riga(Cliente c) {
        return c.ragioneSociale + " - P.IVA: " + valore(c.partitaIva) + " - " + valore(c.indirizzo);
    }

    /** I campi facoltativi sono null a database: meglio dirlo che stampare "null". */
    private String valore(String testo) {
        return testo == null || testo.isBlank() ? "non indicata" : testo;
    }
}
