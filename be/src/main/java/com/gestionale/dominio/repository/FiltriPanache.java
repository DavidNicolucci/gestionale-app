package com.gestionale.dominio.repository;

import io.quarkus.panache.common.Parameters;

// Costruisce la WHERE delle ricerche aggiungendo un pezzo alla volta.
// Prima ogni repository ripeteva lo stesso codice e ogni volta ci scordavamo qualcosa.
//
// Il nome del campo lo scrive sempre il repository, non arriva mai dal client.
// I valori li passiamo come parametri (:nome), mai attaccati alla stringa della query,
// altrimenti si aprirebbe la porta alla SQL injection.
//
// Uso:
//   FiltriPanache f = new FiltriPanache()
//        .contiene("ragioneSociale", req.ragioneSociale)
//        .uguale("cliente.id", req.clienteId);
//   return find(f.where(), sort, f.params());
public final class FiltriPanache {

    // Partiamo da "1=1" cosi' possiamo attaccare tutti gli AND allo stesso modo e
    // funziona anche se non c'e' nessun filtro.
    private final StringBuilder where = new StringBuilder("1=1");
    private final Parameters params = new Parameters();

    // Carattere per proteggere i caratteri speciali nei LIKE. Usiamo '!' e non il
    // backslash perche' quest'ultimo si comporta diversamente da database a database.
    private static final char ESCAPE = '!';

    // Cerca il testo dentro il campo, senza distinguere maiuscole e minuscole.
    // Se il valore e' null o vuoto il filtro non viene aggiunto: svuotare la casella
    // di ricerca non deve azzerare i risultati.
    public FiltriPanache contiene(String campo, String valore) {
        if (valore == null || valore.isBlank()) {
            return this;
        }
        String p = nomeParametro(campo);
        where.append(" and lower(").append(campo).append(") like :").append(p)
             .append(" escape '").append(ESCAPE).append("'");
        params.and(p, "%" + escapeLike(valore.trim().toLowerCase()) + "%");
        return this;
    }

    // Filtro di uguaglianza, per gli id e le relazioni tipo "cliente.id".
    public FiltriPanache uguale(String campo, Object valore) {
        if (valore == null) {
            return this;
        }
        String p = nomeParametro(campo);
        where.append(" and ").append(campo).append(" = :").append(p);
        params.and(p, valore);
        return this;
    }

    public String where() {
        return where.toString();
    }

    public Parameters params() {
        return params;
    }

    // Nel testo cercato '%' e '_' sono caratteri jolly per il database: chi cerca "50%"
    // otterrebbe righe che non c'entrano niente. Ci mettiamo davanti il carattere di
    // escape per farli trattare come testo normale.
    private static String escapeLike(String valore) {
        StringBuilder sb = new StringBuilder(valore.length() + 4);
        for (char c : valore.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                sb.append(ESCAPE);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // "cliente.id" non va bene come nome di parametro: il punto non e' ammesso.
    private static String nomeParametro(String campo) {
        return campo.replace('.', '_');
    }
}
