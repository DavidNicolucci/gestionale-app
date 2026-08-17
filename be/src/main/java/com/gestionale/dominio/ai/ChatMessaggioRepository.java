package com.gestionale.dominio.ai;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class ChatMessaggioRepository implements PanacheRepository<ChatMessaggio> {

    /** Conversazione di un utente, dal messaggio piu' vecchio al piu' recente. */
    public List<ChatMessaggio> perUtente(String username) {
        return find("username = ?1 ORDER BY id", username).list();
    }

    /**
     * Le transazioni stanno qui e non sul service che orchestra: in mezzo ai due
     * salvataggi c'e' la chiamata a Gemini, che dura secondi. Tenendo una sola
     * transazione intorno a tutto, una connessione al database resterebbe
     * occupata per tutta l'attesa di un servizio esterno.
     */
    @Transactional
    public ChatMessaggio salva(String username, AutoreChat autore, String testo) {
        ChatMessaggio messaggio = ChatMessaggio.di(username, autore, testo);
        persist(messaggio);
        return messaggio;
    }

    /** Cancellazione al logout: sparisce tutta la conversazione dell'utente. */
    @Transactional
    public long cancellaPerUtente(String username) {
        return delete("username", username);
    }
}
