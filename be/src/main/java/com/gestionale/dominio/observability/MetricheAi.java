package com.gestionale.dominio.observability;

import com.gestionale.dominio.ai.ChatAiService;
import dev.langchain4j.agent.tool.Tool;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.lang.reflect.Method;

/**
 * Quali strumenti usa davvero l'assistente.
 *
 * Della chiamata a Gemini sapevamo gia' quanto dura e quanto spesso fallisce
 * (@Timed su ConversazioneService), ma non quale strumento il modello sceglie per
 * rispondere. E' l'informazione che serve per due decisioni diverse:
 *
 *   - quali @Tool non viene mai chiamato. Ogni strumento occupa posto nel prompt a
 *     ogni domanda, e uno che nessuno usa e' peso morto che si paga a token.
 *   - se il modello ne sceglie uno sbagliato in modo sistematico. Non e' un bug del
 *     codice: e' la descrizione di quel @Tool che va riscritta, perche' la
 *     descrizione e' l'unica cosa che il modello legge per decidere.
 *
 * I tag sono i nomi dei metodi: pochi, decisi dal codice e non da chi scrive la
 * domanda, quindi nessun rischio di far esplodere le serie in Prometheus (il
 * problema spiegato in {@link MetricheLogin}).
 */
@Startup                // i contatori nascono a zero all'avvio: vedi il costruttore
@ApplicationScoped
public class MetricheAi {

    /** Chiamate agli strumenti dell'assistente, con tag nome=<metodo annotato @Tool>. */
    private static final String TOOL = "gestionale.ai.tool";

    /**
     * Domande arrivate all'assistente, con tag esito=ok|oltre-soglia.
     *
     * Le metriche HTTP contano gia' i 429, ma li mescolano con quelli del login, che
     * raccontano una storia opposta: li' e' qualcuno che prova a entrare, qui e'
     * qualcuno che lavora e ha finito le sue domande. Il rapporto fra i due esiti dice
     * se le soglie di chat.limite.* sono tarate bene o se stanno dando fastidio.
     */
    private static final String DOMANDE = "gestionale.ai.domande";

    public static final String ESITO_OK = "ok";
    /** Rifiutata da {@link com.gestionale.dominio.ai.ProtezioneChat}: nessuna chiamata a Gemini, nessun costo. */
    public static final String ESITO_OLTRE_SOGLIA = "oltre-soglia";

    private final MeterRegistry registry;

    @Inject
    public MetricheAi(MeterRegistry registry) {
        this.registry = registry;
        // Un contatore a zero per ogni strumento esistente, non solo per quelli che
        // qualcuno ha usato. Serve proprio alla domanda "quali non serve tenere": una
        // serie ferma a zero si vede nel grafico, uno strumento che non ha mai creato
        // la sua serie e' invece indistinguibile da uno che non e' mai stato scritto.
        for (Class<?> classeTool : ChatAiService.class.getAnnotation(RegisterAiService.class).tools()) {
            for (Method metodo : classeTool.getDeclaredMethods()) {
                if (metodo.isAnnotationPresent(Tool.class)) {
                    registry.counter(TOOL, "nome", metodo.getName());
                }
            }
        }
        for (String esito : new String[]{ESITO_OK, ESITO_OLTRE_SOGLIA}) {
            registry.counter(DOMANDE, "esito", esito);
        }
    }

    public void toolUsato(String nome) {
        registry.counter(TOOL, "nome", nome).increment();
    }

    public void domanda(String esito) {
        registry.counter(DOMANDE, "esito", esito).increment();
    }
}
