package com.gestionale.dominio.observability;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Incrementa il contatore prima di lasciar partire il metodo.
 *
 * Prima e non dopo: quello che interessa e' quale strumento il modello ha scelto,
 * e la scelta e' gia' stata fatta anche se poi la lettura va storta. Un'eccezione
 * qui dentro non deve comunque impedire la risposta all'utente, e infatti l'unica
 * cosa che fa e' incrementare un contatore in memoria.
 */
@ToolMisurato
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class ToolMisuratoInterceptor {

    @Inject
    MetricheAi metriche;

    @AroundInvoke
    Object conta(InvocationContext contesto) throws Exception {
        metriche.toolUsato(contesto.getMethod().getName());
        return contesto.proceed();
    }
}
