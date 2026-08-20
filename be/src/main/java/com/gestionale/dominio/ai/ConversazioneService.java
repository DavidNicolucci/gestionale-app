package com.gestionale.dominio.ai;

import io.micrometer.core.annotation.Timed;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkiverse.langchain4j.ChatMemoryRemover;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;

/**
 * Tiene insieme le due meta' della conversazione:
 * - lo storico su database, che e' quello che il frontend disegna nella chat;
 * - la memoria del modello, che langchain4j gestisce da solo a partire dal memoryId.
 *
 * Sono separate perche' hanno regole diverse: lo storico e' integrale e vive fino
 * al logout, la memoria e' una finestra sugli ultimi messaggi. Vanno pero'
 * cancellate insieme, altrimenti dopo il logout resterebbe la meta' meno visibile:
 * il modello continuerebbe a ricordarsi la conversazione di chi si e' disconnesso.
 */
@ApplicationScoped
public class ConversazioneService {

    private static final Logger LOG = Logger.getLogger(ConversazioneService.class);

    @Inject
    ChatAiService chatAi;

    @Inject
    ChatMessaggioRepository messaggi;

    /**
     * Salva la domanda, interroga il modello e salva la risposta.
     *
     * Il memoryId e' lo username: cosi' ogni utente ha il suo filo di discorso e
     * nessuno vede il contesto degli altri. Non arriva dal client ma dal JWT
     * (vedi ChatResource): se lo scegliesse il frontend, basterebbe mandare il
     * nome di un collega per leggere la sua conversazione.
     */
    // La chiamata a Gemini e' di gran lunga la cosa piu' lenta che fa l'applicazione,
    // e' verso un servizio esterno e si paga a token: e' esattamente il tipo di
    // operazione che va misurata a parte invece di finire annegata nella metrica
    // generica dell'endpoint. @Timed produce durata, conteggio e - grazie al tag
    // "exception" che aggiunge da solo - anche il tasso di fallimento.
    @Timed(value = "gestionale.ai.chat", description = "Domanda all'assistente AI, dal salvataggio della domanda a quello della risposta")
    @WithSpan("chat assistente")
    public ChatMessaggio rispondi(String username, String domanda) {
        // La lunghezza della domanda sullo span aiuta a spiegare le risposte lente:
        // il testo vero non ci va, finirebbe in chiaro nel collector delle trace.
        Span.current().setAttribute("ai.domanda_caratteri", domanda == null ? 0 : domanda.length());

        // Due transazioni separate, una per salvataggio: in mezzo c'e' la chiamata
        // a Gemini. La domanda resta salvata anche se la risposta non arriva, ed e'
        // giusto cosi': e' quello che l'utente vede nella chat.
        messaggi.salva(username, AutoreChat.UTENTE, domanda);

        // La data di oggi la passiamo noi: il modello non ha un orologio, e senza
        // riferimento interpreterebbe "questo mese" a caso.
        String risposta = chatAi.chat(username, LocalDate.now().toString(), domanda);

        return messaggi.salva(username, AutoreChat.ASSISTENTE, risposta);
    }

    public List<ChatMessaggioResponse> storico(String username) {
        return messaggi.perUtente(username).stream()
                .map(ChatMessaggioResponse::da)
                .toList();
    }

    /**
     * Chiamata al logout. Se salta la pulizia della memoria del modello non
     * blocchiamo la disconnessione: l'utente deve poter uscire comunque.
     */
    public void cancella(String username) {
        long cancellati = messaggi.cancellaPerUtente(username);

        try {
            // API di quarkus-langchain4j: svuota la memoria di quel memoryId sul
            // servizio AI. Passa dal servizio e non dallo store perche' funziona
            // anche se un domani la memoria venisse tenuta da un'altra parte.
            ChatMemoryRemover.remove(chatAi, username);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Storico chat cancellato (%d messaggi) ma la memoria del modello di '%s' e' rimasta",
                    cancellati, username);
        }
    }
}
