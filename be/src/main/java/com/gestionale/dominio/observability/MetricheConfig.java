package com.gestionale.dominio.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Regole valide per tutte le metriche.
 *
 * Un MeterFilter e' un filtro che Micrometer applica a ogni metrica nel momento in
 * cui viene creata: e' il punto giusto per decisioni trasversali, invece di ripeterle
 * su ogni singolo contatore sparso nel codice.
 */
@Singleton
public class MetricheConfig {

    /**
     * Aggiunge il tag "application" a tutte le metriche.
     *
     * Serve quando Prometheus raccoglie piu' servizi: senza questo tag le serie di
     * applicazioni diverse hanno gli stessi nomi (jvm_memory_used_bytes, ...) e in
     * Grafana si sommano tra loro dando numeri che non vogliono dire niente.
     */
    @Produces
    @Singleton
    public MeterFilter tagApplicazione(@ConfigProperty(name = "quarkus.application.name") String applicazione) {
        return MeterFilter.commonTags(Tags.of("application", applicazione));
    }

    /**
     * Fa pubblicare gli istogrammi per i tempi di risposta HTTP e per le nostre
     * metriche "gestionale.*".
     *
     * Di base Micrometer esporta solo conteggio, somma e massimo: con quelli si
     * calcola la media, che sui tempi di risposta e' la statistica meno utile che ci
     * sia (nasconde la coda lenta). Con l'istogramma Prometheus puo' calcolare i
     * percentili veri, p95 e p99, che sono quelli che descrivono l'esperienza reale.
     *
     * L'istogramma non e' gratis - aggiunge una serie temporale per ogni bucket -
     * quindi lo accendiamo solo su queste due famiglie e non su tutto.
     */
    @Produces
    @Singleton
    public MeterFilter istogrammiPercentili() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                String nome = id.getName();
                if (nome.startsWith("http.server.requests") || nome.startsWith("gestionale.")) {
                    return DistributionStatisticConfig.builder()
                            .percentilesHistogram(true)
                            .build()
                            .merge(config);
                }
                return config;
            }
        };
    }
}
