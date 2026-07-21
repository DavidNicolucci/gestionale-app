package com.gestionale.dominio.model.dto;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.ws.rs.WebApplicationException;

import java.util.Set;
import java.util.TreeSet;

// Base comune a tutte le ricerche paginate: numero pagina, righe e ordinamento.
// Ogni dominio la estende aggiungendo i propri filtri (ragioneSociale, cognome...).
// Le pagine partono da 0, sia in richiesta che in risposta: il frontend manda
// (paginaVisuale - 1). Vale per tutto il progetto, cosi' non ci confondiamo.
public abstract class RicercaPaginataRequest {

    public Integer numeroPagina;     // si parte da 0
    public Integer righePerPagina;   // se non arriva, 10
    public String ordinaPer;         // campo su cui ordinare, deve essere tra quelli ammessi
    public String ordinamento;       // "ASC" o "DESC" (se non arriva, DESC)

    private static final int DIM_DEFAULT = 10;
    private static final int DIM_MAX = 100;   // per non farci chiedere pagine enormi

    // Ordiniamo per id quando non ci dicono altro, e lo usiamo anche come secondo
    // criterio: e' l'unico campo sicuramente diverso per ogni riga.
    private static final String CAMPO_ID = "id";

    // Elenco dei campi su cui si puo' ordinare. E' astratto apposta: quando aggiungi
    // una ricerca nuova il compilatore ti obbliga a scriverlo, non te lo scordi.
    protected abstract Set<String> campiOrdinabili();

    // Da sovrascrivere se una ricerca deve ordinare per un campo diverso da id.
    protected String campoOrdineDefault() {
        return CAMPO_ID;
    }

    public int pagina() {
        return (numeroPagina == null || numeroPagina < 0) ? 0 : numeroPagina;
    }

    public int dimensione() {
        int d = (righePerPagina == null || righePerPagina < 1) ? DIM_DEFAULT : righePerPagina;
        return Math.min(d, DIM_MAX);
    }

    public Page pagePanache() {
        return Page.of(pagina(), dimensione());
    }

    // "ordinaPer" arriva dal client e finisce nell'ORDER BY, quindi non lo usiamo mai
    // com'e': lo cerchiamo nella lista dei campi ammessi e usiamo il nome scritto li'.
    // Se non c'e' rispondiamo 400 invece di ordinare per id in silenzio, altrimenti un
    // errore di scrittura lato frontend non lo scopre nessuno.
    //
    // In fondo mettiamo sempre l'id: se due righe hanno lo stesso valore (due clienti
    // con la stessa ragione sociale) il database puo' restituirle in ordine diverso ogni
    // volta, e la stessa riga finirebbe su due pagine o su nessuna.
    public Sort sort() {
        String campo = risolviCampoOrdine();

        Sort.Direction dir = "ASC".equalsIgnoreCase(ordinamento)
                ? Sort.Direction.Ascending
                : Sort.Direction.Descending;

        Sort sort = Sort.by(campo, dir);
        if (!CAMPO_ID.equals(campo)) {
            sort = sort.and(CAMPO_ID, Sort.Direction.Descending);
        }
        return sort;
    }

    private String risolviCampoOrdine() {
        if (ordinaPer == null || ordinaPer.isBlank()) {
            return campoOrdineDefault();
        }
        String richiesto = ordinaPer.trim();
        for (String ammesso : campiOrdinabili()) {
            if (ammesso.equalsIgnoreCase(richiesto)) {
                return ammesso;      // usiamo il nostro nome, non quello del client
            }
        }
        throw new WebApplicationException(
                "Ordinamento non consentito su '" + richiesto + "'. Campi ammessi: "
                        + new TreeSet<>(campiOrdinabili()), 400);
    }
}
