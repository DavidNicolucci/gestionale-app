# ---------------------------------------------------------------------------
# Stadio 1 - compilazione
#
# Maven e il JDK stanno solo qui. Quello che serve a far girare l'applicazione
# e' molto meno di quello che serve a costruirla, e la differenza resta fuori
# dall'immagine finale.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Il pom da solo, prima dei sorgenti: finche' non cambiano le dipendenze Docker
# riusa questo layer. La cache di ~/.m2 sopravvive anche fra build diverse,
# cosi' non si riscarica mezzo Maven Central a ogni modifica.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ---------------------------------------------------------------------------
# Stadio 2 - immagine finale
#
# Solo la JVM e l'applicazione impacchettata. La disposizione delle cartelle e
# l'entrypoint sono quelli dello scaffold Quarkus (src/main/docker/Dockerfile.jvm):
# e' il formato "fast-jar", dove le dipendenze stanno in un layer separato dal
# codice dell'applicazione, che cambia molto piu' spesso.
# ---------------------------------------------------------------------------
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.23

ENV LANGUAGE='en_US:en'

# 185 e' l'utente non-root dell'immagine Red Hat. L'ordine dei COPY non e'
# casuale: prima le librerie (cambiano di rado), poi il codice.
COPY --from=build --chown=185 /app/target/quarkus-app/lib/     /deployments/lib/
COPY --from=build --chown=185 /app/target/quarkus-app/*.jar    /deployments/
COPY --from=build --chown=185 /app/target/quarkus-app/app/     /deployments/app/
COPY --from=build --chown=185 /app/target/quarkus-app/quarkus/ /deployments/quarkus/

# I file caricati durante un import. Senza un volume montato qui sopra spariscono
# con il container.
#
# L'immagine Red Hat e' gia' su utente 185, che in /deployments non puo' creare
# niente: si torna root per il tempo di fare la cartella e si riscende subito.
USER root
RUN mkdir -p /deployments/uploads && chown 185 /deployments/uploads
USER 185

# ---------------------------------------------------------------------------
# Configurazione. Tutto quello che qui sotto e' un default si sovrascrive
# dall'esterno: Quarkus legge le variabili d'ambiente prima del file properties
# (QUARKUS_DATASOURCE_JDBC_URL batte quarkus.datasource.jdbc.url, e cosi' via).
# ---------------------------------------------------------------------------

# In ascolto su tutte le interfacce: di default Quarkus si lega a 127.0.0.1, che
# dentro un container vuol dire raggiungibile solo da se stesso.
#
# Qui si parla HTTP in chiaro sulla 8080, non HTTPS: dentro la rete di Docker il
# traffico non esce da nessuna parte, e il certificato al mondo esterno lo
# presenta nginx, che e' il confine. Il blocco TLS in application.properties e'
# sotto %dev proprio per questo.
ENV QUARKUS_HTTP_HOST=0.0.0.0

# Le chiavi con cui si firmano e si verificano i token di sessione. NON sono
# nell'immagine di proposito: una chiave privata dentro un layer e' una chiave
# che viaggia con l'immagine ovunque la si copi. Si monta una cartella su
# /keys e queste due righe la indicano a Quarkus.
ENV SMALLRYE_JWT_SIGN_KEY_LOCATION=file:/keys/privateKey.pem
ENV MP_JWT_VERIFY_PUBLICKEY_LOCATION=file:/keys/publicKey.pem

ENV IMPORT_UPLOAD_DIR=/deployments/uploads

EXPOSE 8080 9000

ENV JAVA_OPTS_APPEND="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
