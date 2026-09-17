package com.gestionale.dominio.api;

import com.gestionale.dominio.auth.service.EmissioneToken;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

/**
 * I clienti sono la sezione di riferimento del progetto: quello che si prova qui vale,
 * uguale, per dipendenti, siti e timesheet.
 *
 * Non si prova il CRUD in se' - quello e' Panache e funziona - ma il contratto HTTP che
 * gli sta intorno: chi puo' chiamare cosa, che aspetto ha un errore di validazione, che
 * codice torna un id che non esiste. Sono le tre cose che il frontend si aspetta e che
 * nessun test di service puo' garantire.
 */
@QuarkusTest
class ClientiApiTest extends ApiTest {

    @Test
    void senzaSessioneNonSiLeggeNiente() {
        given()
                .when()
                .get("/api/clienti")
                .then()
                .statusCode(401);
    }

    @Test
    void lOperatoreLeggeMaNonCrea() {
        // La differenza fra i due ruoli sta tutta in un'annotazione su un metodo:
        // @RolesAllowed({"ADMIN", "OPERATOR"}) sulla lettura, @RolesAllowed("ADMIN")
        // sulla scrittura. Se qualcuno la cambia, solo una chiamata vera se ne accorge.
        String cookie = accedi(OPERATORE);

        given()
                .cookie(EmissioneToken.COOKIE_NAME, cookie)
                .when()
                .get("/api/clienti")
                .then()
                .statusCode(200);

        given()
                .cookie(EmissioneToken.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body(Map.of("ragioneSociale", "Vietata SRL"))
                .when()
                .post("/api/clienti")
                .then()
                .statusCode(403);
    }

    @Test
    void laRagioneSocialeVuotaTornaUnErroreSulCampoGiusto() {
        // Il frontend disegna il messaggio sotto la casella sbagliata leggendo
        // fieldErrors: se il percorso del campo cambiasse (e' "crea.req.ragioneSociale"
        // prima che il mapper lo accorci), l'errore arriverebbe senza sapere dove
        // metterlo e la pagina resterebbe muta.
        given()
                .cookie(EmissioneToken.COOKIE_NAME, accedi(ADMIN))
                .contentType(ContentType.JSON)
                .body(Map.of("ragioneSociale", "  "))
                .when()
                .post("/api/clienti")
                .then()
                .statusCode(400)
                .body("fieldErrors.ragioneSociale", notNullValue())
                .body("fieldErrors.ragioneSociale", hasItem("La ragione sociale è obbligatoria"))
                .body("message", equalTo("Dati non validi"));
    }

    @Test
    void laPartitaIvaTroppoLungaVieneRifiutata() {
        given()
                .cookie(EmissioneToken.COOKIE_NAME, accedi(ADMIN))
                .contentType(ContentType.JSON)
                .body(Map.of("ragioneSociale", "Troppo Lunga SRL", "partitaIva", "0".repeat(21)))
                .when()
                .post("/api/clienti")
                .then()
                .statusCode(400)
                .body("fieldErrors.partitaIva", notNullValue());
    }

    @Test
    void unClienteCreatoSiRitrovaDovEStatoMesso() {
        String cookie = accedi(ADMIN);

        Integer id = given()
                .cookie(EmissioneToken.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body(Map.of("ragioneSociale", "Nuova Impresa SRL",
                        "partitaIva", "99887766554",
                        "indirizzo", "Via Verdi 10, Torino"))
                .when()
                .post("/api/clienti")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("ragioneSociale", equalTo("Nuova Impresa SRL"))
                .body("eliminato", equalTo(false))
                .extract().path("id");

        given()
                .cookie(EmissioneToken.COOKIE_NAME, cookie)
                .when()
                .get("/api/clienti/" + id)
                .then()
                .statusCode(200)
                .body("partitaIva", equalTo("99887766554"))
                .body("indirizzo", equalTo("Via Verdi 10, Torino"));
    }

    @Test
    void unIdCheNonEsisteE404ENon500() {
        // Il service lancia una NotFoundException con dentro la frase giusta: se un
        // domani diventasse un'eccezione qualsiasi, il client vedrebbe un 500 e
        // "errore imprevisto" al posto di "non esiste".
        given()
                .cookie(EmissioneToken.COOKIE_NAME, accedi(ADMIN))
                .when()
                .get("/api/clienti/999999")
                .then()
                .statusCode(404)
                .body("status", equalTo(404))
                .body("message", notNullValue());
    }

    @Test
    void lEliminazioneELogicaEIlClienteSiPuoRipristinare() {
        // Cancellazione logica (decisione 15): la riga resta, perche' i dati collegati
        // la usano ancora. Da fuori si vede solo dal campo "eliminato" e dal fatto che
        // /ripristino lo riporta indietro.
        String cookie = accedi(ADMIN);

        Integer id = given()
                .cookie(EmissioneToken.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body(Map.of("ragioneSociale", "Da Eliminare SRL"))
                .when()
                .post("/api/clienti")
                .then()
                .statusCode(200)
                .extract().path("id");

        given()
                .cookie(EmissioneToken.COOKIE_NAME, cookie)
                .when()
                .delete("/api/clienti/" + id)
                .then()
                .statusCode(204);

        given()
                .cookie(EmissioneToken.COOKIE_NAME, cookie)
                .when()
                .get("/api/clienti/" + id)
                .then()
                .statusCode(200)
                .body("eliminato", equalTo(true));

        given()
                .cookie(EmissioneToken.COOKIE_NAME, cookie)
                // Corpo vuoto ma Content-Type json, come lo manda il frontend
                // (`post(url, {})`): l'endpoint dichiara @Consumes(application/json).
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/clienti/" + id + "/ripristino")
                .then()
                .statusCode(200)
                .body("eliminato", equalTo(false));
    }
}
