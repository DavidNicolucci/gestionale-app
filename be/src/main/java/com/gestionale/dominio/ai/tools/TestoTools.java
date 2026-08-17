package com.gestionale.dominio.ai.tools;

import com.gestionale.dominio.repository.RiepilogoOre;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * Formattazione delle risposte degli strumenti.
 *
 * Quello che tornano i @Tool non e' un DTO per un frontend: e' testo che finisce
 * dentro al prompt del modello. Quindi conta che sia breve (ogni riga costa
 * token), non ambiguo e che dica esplicitamente quando un elenco e' stato
 * troncato, altrimenti il modello presenta 50 righe come se fossero tutte.
 */
final class TestoTools {

    /**
     * Tetto alle righe di un elenco. Serve a non far esplodere il prompt quando
     * qualcuno chiede "elencami tutto" su una tabella con migliaia di record.
     */
    static final int MAX_RIGHE = 50;

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private TestoTools() {
    }

    /** Elenco numerato, con avviso finale se e' stato tagliato. */
    static <T> String elenco(List<T> elementi, Function<T, String> riga, String seVuoto) {
        if (elementi.isEmpty()) {
            return seVuoto;
        }

        StringBuilder testo = new StringBuilder();
        elementi.stream().limit(MAX_RIGHE).forEach(e -> testo.append("- ").append(riga.apply(e)).append("\n"));

        // Il limite lo applica gia' la query: se torna esattamente MAX_RIGHE non
        // sappiamo se ce ne fossero altre, e va detto invece di far finta di no.
        if (elementi.size() >= MAX_RIGHE) {
            testo.append("(elenco troncato ai primi ").append(MAX_RIGHE)
                    .append(": chiedi all'utente un criterio piu' preciso se serve il resto)");
        }

        return testo.toString();
    }

    /** Righe di un raggruppamento di ore, con il totale in fondo. */
    static String riepilogo(List<RiepilogoOre> righe, String seVuoto) {
        if (righe.isEmpty()) {
            return seVuoto;
        }

        BigDecimal totale = righe.stream()
                .map(RiepilogoOre::ore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return elenco(righe, r -> r.etichetta() + ": " + ore(r.ore()), seVuoto)
                + "Totale complessivo: " + ore(totale) + " ore";
    }

    /** "7.50" diventa "7.5" e "8.00" diventa "8": il modello lo rilegge piu' naturalmente. */
    static String ore(BigDecimal ore) {
        return ore == null ? "0" : ore.stripTrailingZeros().toPlainString();
    }

    static String data(LocalDate data) {
        return data == null ? "-" : data.format(DATA);
    }
}
