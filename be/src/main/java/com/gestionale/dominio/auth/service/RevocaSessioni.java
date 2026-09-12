package com.gestionale.dominio.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gestionale.dominio.security.entity.AppUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Permette di far scadere un token prima della sua scadenza.
 *
 * Il problema che risolve: un JWT firmato e' valido finche' non scade, e il server
 * non tiene traccia di quelli emessi. Chi ne ottiene una copia (un log di proxy, un
 * dump di memoria, un computer lasciato aperto) puo' usarla con curl fino alla
 * scadenza, e nemmeno il logout la ferma: quello cancella il cookie nel browser, non
 * il token. Stesso discorso per un account disattivato o per una password cambiata
 * dopo un sospetto: senza un controllo in piu', il vecchio token continua a entrare.
 *
 * Come lo risolve: ogni utente ha in app_user un numero, token_epoch. Il numero
 * viene copiato dentro il token al login, e a ogni richiesta si confronta quello del
 * token con quello del database. Incrementare la colonna fa cadere di colpo tutti i
 * token emessi prima, senza tenere un elenco dei token vivi: lo stato da conservare
 * e' un intero per utente, non uno per sessione.
 *
 * Il prezzo e' una lettura del database a ogni richiesta, e per questo c'e' una
 * cache di pochi secondi. La revoca resta comunque immediata, perche' chi incrementa
 * la colonna svuota anche la voce di cache. Ma la cache e' in memoria: con piu'
 * istanze dietro un bilanciatore, la revoca fatta su una macchina resterebbe
 * invisibile alle altre per la durata della cache (sessione.cache-stato). A quel
 * punto la cache andrebbe spostata su Redis, oppure accorciata fino a zero.
 *
 * Nota: la revoca vale per l'utente, non per il singolo accesso. Chi esce dal
 * computer dell'ufficio chiude anche la sessione che aveva sul telefono. Per
 * distinguerle servirebbe un identificativo di sessione dentro il token e un elenco
 * per utente, cioe' molto piu' stato di un intero.
 */
@ApplicationScoped
public class RevocaSessioni {

    private static final Logger LOG = Logger.getLogger(RevocaSessioni.class);

    /**
     * Cosa ci serve sapere di un utente a ogni richiesta.
     *
     * @param attivo     false se l'account e' stato disattivato: il token va rifiutato
     *                   anche se nessuno ha incrementato token_epoch
     * @param tokenEpoch l'epoca buona adesso: i token che ne portano una diversa
     *                   sono stati revocati
     */
    public record Stato(boolean attivo, int tokenEpoch) {
    }

    private final Cache<String, Optional<Stato>> cache;

    @Inject
    public RevocaSessioni(@ConfigProperty(name = "sessione.cache-stato") Duration durataCache) {
        this.cache = Caffeine.newBuilder()
                // Senza tetto, un token con un upn inventato per ogni richiesta
                // riempirebbe la memoria di risultati vuoti. In pratica le chiavi sono
                // quante gli utenti, ma la firma del token non e' l'unica cosa da cui
                // difendersi.
                .maximumSize(10_000)
                .expireAfterWrite(durataCache)
                .build();
    }

    /**
     * Lo stato dell'utente, dalla cache o dal database. Vuoto se l'utente non esiste
     * piu': un token che parla di un account cancellato non vale niente.
     */
    @Transactional
    public Optional<Stato> stato(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return cache.get(chiave(username), k -> leggiDalDatabase(username));
    }

    private Optional<Stato> leggiDalDatabase(String username) {
        return AppUser.<AppUser>find("username", username)
                .firstResultOptional()
                .map(u -> new Stato(u.enabled, u.tokenEpoch));
    }

    /**
     * Chiude tutte le sessioni dell'utente indicato: i token gia' in giro, compresa
     * la copia che qualcuno potrebbe essersi portato via, smettono di funzionare.
     *
     * @param motivo cosa l'ha provocata, per il log: "logout", "cambio password"...
     */
    @Transactional
    public void revoca(String username, String motivo) {
        long righe = AppUser.update("tokenEpoch = tokenEpoch + 1 where username = ?1", username);
        cache.invalidate(chiave(username));
        if (righe > 0) {
            LOG.infof("Sessioni revocate per %s (%s)", username, motivo);
        }
    }

    /**
     * Come {@link #revoca(String, String)}, ma su un'entita' gia' caricata dentro una
     * transazione: l'incremento viene salvato al commit insieme al resto della
     * modifica. Cosi' una password cambiata e le sue sessioni chiuse sono la stessa
     * operazione, e non puo' riuscirne una senza l'altra.
     */
    public void revoca(AppUser utente, String motivo) {
        utente.tokenEpoch++;
        cache.invalidate(chiave(utente.username));
        LOG.infof("Sessioni revocate per %s (%s)", utente.username, motivo);
    }

    /**
     * SQL Server confronta gli username ignorando maiuscole e spazi finali, quindi
     * "Mario" e "mario " sono lo stesso account: senza normalizzare, la revoca
     * svuoterebbe una voce di cache e ne lascerebbe viva un'altra dello stesso utente.
     */
    private static String chiave(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }
}
