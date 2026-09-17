package com.gestionale.dominio.api;

import com.gestionale.dominio.auth.service.EmissioneToken;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Il giro completo della sessione: entrare, essere riconosciuti, uscire.
 *
 * L'ultimo test e' quello che conta piu' degli altri. Il logout cancella il cookie nel
 * browser, ma il token resta firmato e valido fino alla scadenza: se qualcuno se n'e'
 * portato via una copia, cancellare il cookie non la ferma. La revoca (token_epoch)
 * serve esattamente a questo, e l'unico modo di verificarla e' rimandare il vecchio
 * cookie dopo il logout - cosa che un test sui service non puo' fare.
 */
@QuarkusTest
class AutenticazioneApiTest extends ApiTest {

    @Test
    void conLeCredenzialiGiusteSiEntraESiRicevonoIRuoli() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", ADMIN, "password", PASSWORD))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("username", equalTo(ADMIN))
                .body("ruoli", hasItem("ADMIN"));
    }

    @Test
    void ilTokenViaggiaInUnCookieCheIlJavaScriptNonPuoLeggere() {
        // E' la ragione per cui il token non sta in localStorage: un XSS, anche da una
        // dipendenza npm compromessa, non deve poterselo prendere.
        Response risposta = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", ADMIN, "password", PASSWORD))
                .when()
                .post("/api/auth/login");

        risposta.then().statusCode(200);
        String cookie = risposta.getDetailedCookie(EmissioneToken.COOKIE_NAME).toString();

        assertTrue(cookie.contains("HttpOnly"), cookie);
        assertTrue(cookie.contains("Secure"), cookie);
        assertTrue(cookie.contains("SameSite=Strict"), cookie);
        // Il corpo della risposta non deve contenere il token: sarebbe leggibile da JS.
        assertEquals(-1, risposta.getBody().asString().indexOf("eyJ"));
    }

    @Test
    void laPasswordSbagliataNonDiceSeLUtenteEsiste() {
        // Stesso 401 e stesso messaggio sia per l'utente vero con password sbagliata,
        // sia per uno che non esiste: la differenza direbbe quali account sono validi.
        String messaggioUtenteVero = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", ADMIN, "password", "sbagliata"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .extract().path("message");

        String messaggioUtenteInventato = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "nessuno-di-questo-nome", "password", "sbagliata"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .extract().path("message");

        assertEquals(messaggioUtenteVero, messaggioUtenteInventato);
    }

    @Test
    void lUtenteDisattivatoNonEntraNemmeneConLaPasswordGiusta() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "sospeso", "password", PASSWORD))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void ilLoginSenzaCredenzialiDiceQualiCampiMancano() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(400)
                .body("fieldErrors.username", notNullValue())
                .body("fieldErrors.password", notNullValue());
    }

    @Test
    void senzaSessioneNonSiSaChiSiamo() {
        given()
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    void conLaSessioneApertaSiSaChiSiamo() {
        given()
                .cookie(EmissioneToken.COOKIE_NAME, accedi(OPERATORE))
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .body("username", equalTo(OPERATORE))
                .body("ruoli", hasItem("OPERATOR"));
    }

    @Test
    void dopoIlLogoutIlVecchioTokenNonValePiu() throws Exception {
        String cookie = accedi(ADMIN);

        // La chiamata e' fatta come la fa il browser - nessun corpo e nessun
        // Content-Type, cioe' `this.http.post('/api/auth/logout', null)` - e non come
        // farebbe comodo al test: l'endpoint dichiara @Consumes(application/json), e se
        // pretendesse davvero quell'intestazione il logout risponderebbe 415 e la
        // sessione non verrebbe revocata. E' la differenza fra "esco" e "credo di
        // essere uscito", quindi va provata esattamente cosi'.
        assertEquals(204, postSenzaIntestazioni("/api/auth/logout", cookie).statusCode(),
                "il logout deve funzionare anche senza Content-Type: e' come lo manda il frontend");

        // Il token non e' scaduto e la firma e' ancora buona: a fermarlo e' solo il
        // confronto fra l'epoca scritta dentro e quella sul database, che il logout ha
        // incrementato. Senza quel controllo, questa chiamata risponderebbe 200.
        given()
                .cookie(EmissioneToken.COOKIE_NAME, cookie)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(401)
                .body("message", containsString("Sessione"));
    }
}
