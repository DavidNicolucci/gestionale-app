package com.gestionale.dominio.ai.tools;

import com.gestionale.dominio.model.entity.Cliente;
import com.gestionale.dominio.model.entity.Sito;
import com.gestionale.dominio.repository.ClienteRepository;
import com.gestionale.dominio.repository.SitoRepository;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

/**
 * Cosa l'assistente puo' sapere sui siti, cioe' i luoghi dove si lavora
 * (cantieri, negozi, sedi). Ogni sito appartiene a un cliente. Sono tutte letture.
 */
@ApplicationScoped
public class SitoTools {

    @Inject
    SitoRepository siti;

    @Inject
    ClienteRepository clienti;

    @Tool("""
            Elenca i siti registrati - cantieri, negozi, sedi operative - con il cliente a cui appartengono.
            Usalo quando l'utente chiede quali sono i siti, i cantieri o i luoghi di lavoro, oppure quanti sono.
            """)
    public String elencaSiti() {
        List<Sito> trovati = siti.elencoConCliente(TestoTools.MAX_RIGHE);

        return "Siti registrati: " + siti.count() + ".\n"
                + TestoTools.elenco(trovati, this::riga, "Nessun sito registrato.");
    }

    @Tool("""
            Cerca i siti il cui nome o indirizzo contiene il testo indicato.
            Usalo quando l'utente nomina un cantiere o un luogo in modo parziale,
            per esempio "il cantiere di via Roma" o "cerca boutique".
            """)
    public String cercaSiti(String testo) {
        List<Sito> trovati = siti.cercaTestuale(testo, TestoTools.MAX_RIGHE);

        return TestoTools.elenco(trovati, this::riga, "Nessun sito trovato per '" + testo + "'.");
    }

    @Tool("""
            Elenca i siti di un cliente specifico. Il parametro e' la ragione sociale del cliente.
            Usalo per domande come "quali cantieri ha Acme" o "dove lavoriamo per quel cliente".
            """)
    public String sitiDelCliente(String ragioneSociale) {
        Optional<Cliente> cliente = clienti.perRagioneSociale(ragioneSociale);

        if (cliente.isEmpty()) {
            return "Nessun cliente trovato con ragione sociale '" + ragioneSociale
                    + "'. Prova cercaClienti per identificarlo.";
        }

        List<Sito> suoiSiti = siti.perCliente(cliente.get().id);

        return TestoTools.elenco(suoiSiti,
                s -> s.nome + (s.indirizzo == null ? "" : " - " + s.indirizzo),
                "Il cliente " + cliente.get().ragioneSociale + " non ha siti collegati.");
    }

    private String riga(Sito s) {
        return s.nome
                + (s.indirizzo == null ? "" : " (" + s.indirizzo + ")")
                + " - cliente: " + s.cliente.ragioneSociale;
    }
}
