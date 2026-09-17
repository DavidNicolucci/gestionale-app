package com.gestionale.dominio.api;

import com.gestionale.dominio.auth.service.EmissioneToken;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Base dei test che provano gli endpoint sul serio: applicazione avviata, HTTP vero,
 * database vero (un SQL Server usa-e-getta, vedi la sezione TEST di
 * application.properties).
 *
 * Perche' servono, visto che i service e la validazione sono gia' coperti altrove:
 * quello che sta *fra* le classi non lo prova nessun test unitario. Un @RolesAllowed
 * scritto male, un exception mapper che non viene registrato, un cookie senza HttpOnly,
 * un 404 che diventa 500: sono tutte cose che compilano, passano i test delle classi e
 * si vedono solo quando qualcuno fa la richiesta.
 *
 * Il login qui e' quello vero - username, password, cookie - e non un'identita' finta
 * iniettata nel test: cosi' ogni chiamata autenticata attraversa anche FiltroSessione e
 * il controllo delle revoche, che e' la parte che vogliamo davvero vedere funzionare.
 */
@QuarkusTest
abstract class ApiTest {

    /** Gli utenti di src/test/resources/import.sql. La password e' la stessa per tutti. */
    protected static final String ADMIN = "admin";
    protected static final String OPERATORE = "operatore";
    protected static final String PASSWORD = "admin123";

    @BeforeAll
    static void accettaIlCertificatoDiSviluppo() {
        // L'applicazione parla HTTPS con un certificato autofirmato, in test come in
        // sviluppo. Lo accettiamo invece di spegnere TLS nei test: cosi' le chiamate
        // passano dallo stesso pezzo di configurazione che useranno davvero.
        RestAssured.useRelaxedHTTPSValidation();
        // Il certificato e' intestato a localhost ma il client del JDK e' schizzinoso
        // lo stesso: senza questa riga postSenzaIntestazioni non arriverebbe in fondo.
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
    }

    /**
     * Una POST senza corpo e senza NESSUN Content-Type, come la manda il browser quando
     * il frontend scrive `this.http.post(url, null)`.
     *
     * Non si puo' fare con RestAssured: un Content-Type lo mette comunque (text/plain,
     * o vuoto se glielo si azzera), e su un endpoint @Consumes(application/json) quello
     * vale un 415 - il test fallirebbe per un motivo che nel browser non esiste. Qui
     * serve il client HTTP del JDK, che se non gli si dice niente non aggiunge niente.
     *
     * Il certificato e' autofirmato come in sviluppo, quindi il client si fida di
     * qualunque certificato: e' un test in locale contro la nostra applicazione, non
     * c'e' nessun canale da proteggere.
     */
    protected static HttpResponse<String> postSenzaIntestazioni(String percorso, String cookie) throws Exception {
        SSLContext contesto = SSLContext.getInstance("TLS");
        contesto.init(null, new TrustManager[]{FIDATI_DI_TUTTO}, null);

        HttpClient client = HttpClient.newBuilder().sslContext(contesto).build();
        HttpRequest richiesta = HttpRequest.newBuilder()
                .uri(URI.create(RestAssured.baseURI + ":" + RestAssured.port + percorso))
                .header("Cookie", EmissioneToken.COOKIE_NAME + "=" + cookie)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return client.send(richiesta, HttpResponse.BodyHandlers.ofString());
    }

    private static final X509TrustManager FIDATI_DI_TUTTO = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] catena, String tipo) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] catena, String tipo) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    /** Fa il login e restituisce il cookie di sessione da rimandare nelle chiamate. */
    protected static String accedi(String username) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", PASSWORD))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .cookie(EmissioneToken.COOKIE_NAME);
    }
}
