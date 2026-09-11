package com.gestionale.dominio.security.model;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuoloTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void preparaValidator() {
        // ParameterMessageInterpolator: niente Expression Language, che fuori da
        // Quarkus non c'e' sul classpath (e ai nostri messaggi non serve).
        factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void chiudi() {
        factory.close();
    }

    // ---- L'enum combacia con i @RolesAllowed del codice ----

    /**
     * Legge i @RolesAllowed da tutte le classi compilate e li confronta con l'enum,
     * nei due sensi:
     *   - un ruolo usato in un @RolesAllowed ma assente dall'enum non si potrebbe
     *     assegnare a nessuno;
     *   - un ruolo dell'enum che nessun @RolesAllowed nomina darebbe un utente che
     *     entra e non puo' fare niente (era il caso del vecchio default "USER").
     */
    @Test
    void lEnumContieneEsattamenteIRuoliDeiRolesAllowed() throws Exception {
        Set<String> usati = ruoliNominatiNeiRolesAllowed();
        Set<String> enumerati = Arrays.stream(Ruolo.values()).map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));

        assertFalse(usati.isEmpty(), "Nessun @RolesAllowed trovato: la scansione delle classi non funziona");
        assertEquals(enumerati, usati,
                "I ruoli dell'enum Ruolo e quelli nominati nei @RolesAllowed devono coincidere");
    }

    /**
     * Il vincolo ck_user_role_nome dello schema deve ammettere gli stessi ruoli
     * dell'enum: se ne manca uno, l'INSERT di un utente con quel ruolo fallisce con un
     * errore del database; se ce n'e' uno in piu', il database accetta un ruolo inutile.
     */
    @Test
    void ilVincoloDelDatabaseAmmetteGliStessiRuoliDellEnum() throws IOException {
        // I test partono da be/, lo schema sta in ../docker
        String schema = Files.readString(Path.of("..", "docker", "sqlserver", "init", "01-schema.sql"));
        Matcher vincolo = Pattern.compile("ADD CONSTRAINT ck_user_role_nome CHECK \\((.*?)\\);", Pattern.DOTALL)
                .matcher(schema);
        assertTrue(vincolo.find(), "Vincolo ck_user_role_nome non trovato in 01-schema.sql");

        Set<String> nelDatabase = new TreeSet<>();
        Matcher valore = Pattern.compile("LIKE N'([^']*)'").matcher(vincolo.group(1));
        while (valore.find()) {
            nelDatabase.add(valore.group(1));
        }
        Set<String> enumerati = Arrays.stream(Ruolo.values()).map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(enumerati, nelDatabase,
                "I ruoli del vincolo ck_user_role_nome devono coincidere con quelli dell'enum Ruolo");
    }

    // ---- Ruolo.da ----

    @Test
    void maiuscoleESpaziNonContano() {
        assertEquals(Ruolo.ADMIN, Ruolo.da("admin").orElseThrow());
        assertEquals(Ruolo.OPERATOR, Ruolo.da(" Operator ").orElseThrow());
    }

    @Test
    void unRuoloInventatoNonEsiste() {
        assertTrue(Ruolo.da("ADMINN").isEmpty());
        assertTrue(Ruolo.da("pippo").isEmpty());
        assertTrue(Ruolo.da("USER").isEmpty());
        assertTrue(Ruolo.da("").isEmpty());
        assertTrue(Ruolo.da(null).isEmpty());
    }

    // ---- Validazione della richiesta di creazione utente ----

    @Test
    void ruoliValidiPassano() {
        assertTrue(erroriSuRuoli(List.of("ADMIN")).isEmpty());
        assertTrue(erroriSuRuoli(List.of("admin", "OPERATOR")).isEmpty());
    }

    @Test
    void unRuoloInventatoDaErroreConIRuoliAmmessi() {
        List<String> errori = erroriSuRuoli(List.of("OPERATOR", "ADMINN"));

        assertEquals(List.of("Ruolo non valido: i ruoli ammessi sono ADMIN, OPERATOR"), errori);
    }

    @Test
    void senzaRuoliDaErrore() {
        assertEquals(List.of("Indicare almeno un ruolo: i ruoli ammessi sono ADMIN, OPERATOR"),
                erroriSuRuoli(List.of()));
        assertEquals(List.of("Indicare almeno un ruolo: i ruoli ammessi sono ADMIN, OPERATOR"),
                erroriSuRuoli(null));
    }

    @Test
    void unValoreConEspressioniNonVieneInterpretato() {
        // Il valore sbagliato non finisce nel messaggio, quindi un "${...}" mandato
        // dal client non puo' essere valutato dall'interpolazione.
        List<String> errori = erroriSuRuoli(List.of("${1+1}"));

        assertEquals(List.of("Ruolo non valido: i ruoli ammessi sono ADMIN, OPERATOR"), errori);
    }

    private static List<String> erroriSuRuoli(List<String> ruoli) {
        CreateUserRequest req = new CreateUserRequest();
        req.username = "nuovo";
        req.password = "passwordLunga1";
        req.ruoli = ruoli;
        return validator.validate(req).stream()
                .filter(v -> v.getPropertyPath().toString().equals("ruoli"))
                .map(ConstraintViolation::getMessage)
                .toList();
    }

    private static Set<String> ruoliNominatiNeiRolesAllowed() throws IOException, URISyntaxException {
        Path classi = Path.of(Ruolo.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ClassLoader loader = RuoloTest.class.getClassLoader();
        Set<String> ruoli = new TreeSet<>();

        try (Stream<Path> file = Files.walk(classi)) {
            for (Path p : file.filter(f -> f.toString().endsWith(".class")).toList()) {
                String nome = classi.relativize(p).toString()
                        .replace(p.getFileSystem().getSeparator(), ".")
                        .replaceAll("\\.class$", "");
                Class<?> classe;
                try {
                    classe = Class.forName(nome, false, loader);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Classe non caricabile: " + nome, e);
                }
                aggiungi(classe, ruoli);
                for (Method m : classe.getDeclaredMethods()) {
                    aggiungi(m, ruoli);
                }
            }
        }
        return ruoli;
    }

    private static void aggiungi(AnnotatedElement elemento, Set<String> ruoli) {
        RolesAllowed ra = elemento.getAnnotation(RolesAllowed.class);
        if (ra != null) {
            ruoli.addAll(Arrays.asList(ra.value()));
        }
    }
}
