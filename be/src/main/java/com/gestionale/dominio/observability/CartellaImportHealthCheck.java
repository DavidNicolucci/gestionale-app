package com.gestionale.dominio.observability;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Controlla che la cartella degli upload sia scrivibile.
 *
 * I check su database e RabbitMQ li registra Quarkus da solo, ma di questa cartella
 * non sa niente: se il disco e' pieno o i permessi sono sbagliati l'applicazione
 * risponde regolarmente a tutti gli endpoint e sembra sana, poi ogni upload fallisce
 * con un 500. E' @Readiness e non @Liveness proprio per questo: il processo e' vivo,
 * semplicemente non e' in grado di lavorare, quindi va tolto dal traffico ma non
 * riavviato (riavviarlo non libererebbe il disco).
 */
@Readiness
@ApplicationScoped
public class CartellaImportHealthCheck implements HealthCheck {

    private static final String NOME = "cartella import scrivibile";

    @ConfigProperty(name = "import.upload-dir")
    String uploadDir;

    @Override
    public HealthCheckResponse call() {
        Path dir = Paths.get(uploadDir);
        try {
            Files.createDirectories(dir);

            // Non usiamo Files.isWritable: su Windows guarda l'attributo "sola lettura",
            // che sulle cartelle non vuol dire niente e risponderebbe sempre di si'.
            // L'unico modo affidabile e' provare a scrivere davvero.
            Path prova = Files.createTempFile(dir, ".healthcheck", null);
            Files.delete(prova);

            return HealthCheckResponse.named(NOME)
                    .withData("cartella", dir.toAbsolutePath().toString())
                    .up()
                    .build();

        } catch (Exception e) {
            return HealthCheckResponse.named(NOME)
                    .withData("cartella", dir.toAbsolutePath().toString())
                    .withData("errore", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .down()
                    .build();
        }
    }
}
