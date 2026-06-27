package com.gestionale.dominio.ai;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(tools = TimesheetTools.class)   // collega i tool a questo servizio AI
public interface ChatAiService {

    @SystemMessage("""
        Sei l'assistente di un gestionale aziendale per la gestione delle ore dei dipendenti.
        Rispondi in italiano, in modo conciso e professionale.
        Quando ti vengono chieste informazioni su dipendenti o ore lavorate,
        usa gli strumenti disponibili per recuperare i dati reali dal database.
        Se non trovi un dato, dillo chiaramente invece di inventare.
        La data odierna serve come riferimento per interpretare richieste come "questo mese".
        """)
    String chat(String messaggioUtente);
}