package com.gestionale.dominio.observability;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sulla classe di strumenti dell'assistente: ogni metodo @Tool che contiene viene
 * contato in {@link MetricheAi}.
 *
 * Sta sulla classe e non sui singoli metodi perche' il nome del tag lo ricava
 * l'intercettore dal metodo chiamato: cosi' non c'e' un nome scritto due volte che
 * un domani qualcuno rinomina per meta'.
 */
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolMisurato {
}
