package com.gestionale.dominio.ai;

import com.gestionale.dominio.ai.tools.ClienteTools;
import com.gestionale.dominio.ai.tools.DipendenteTools;
import com.gestionale.dominio.ai.tools.SitoTools;
import com.gestionale.dominio.ai.tools.TimesheetTools;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

// I "tools" sono i metodi che l'AI puo' chiamare per leggere i dati veri dal database.
// Una classe per area: quello che l'assistente sa fare e' esattamente la somma dei
// metodi @Tool qui elencati, niente di piu'.
@RegisterAiService(tools = {
        DipendenteTools.class,
        ClienteTools.class,
        SitoTools.class,
        TimesheetTools.class
})
public interface ChatAiService {

    @SystemMessage("""
        Sei l'assistente di un gestionale aziendale che tiene l'anagrafica di dipendenti,
        clienti e siti di lavoro, e le ore lavorate dai dipendenti sui siti.
        Rispondi in italiano, in modo conciso e professionale.

        Usa sempre gli strumenti disponibili per recuperare i dati reali dal database:
        non rispondere a memoria e non inventare mai nomi, numeri o totali.
        Se un nome non viene trovato, prima prova a cercarlo con gli strumenti di ricerca,
        che accettano anche solo una parte del nome. Se i risultati sono piu' di uno,
        chiedi all'utente quale intendeva invece di sceglierne uno a caso.
        Se un dato non c'e', dillo chiaramente.

        Puoi solo leggere: non sei in grado di inserire, modificare o cancellare nulla.
        Se ti viene chiesto di farlo, spiega che va fatto dalle pagine del gestionale.

        Oggi e' il {{oggi}}: usalo come riferimento per interpretare richieste come
        "questo mese", "la settimana scorsa" o "quest'anno", convertendole sempre
        in un intervallo di date preciso da passare agli strumenti.
        """)
    String chat(@MemoryId String utente, @V("oggi") String oggi, @UserMessage String messaggioUtente);
}
