package com.gestionale.dominio.ai;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Un messaggio della conversazione, cosi' come viene mostrato nella chat.
 *
 * E' una cosa diversa dalla memoria del modello: qui c'e' lo storico integrale,
 * mentre a Gemini viene passata solo una finestra degli ultimi messaggi
 * (quarkus.langchain4j.chat-memory.memory-window.max-messages). Tenerli separati
 * permette di mostrare tutta la conversazione senza far crescere - e pagare -
 * il contesto mandato al modello a ogni domanda.
 *
 * La chiave e' lo username e non l'id utente: e' lo stesso valore che finisce
 * nel JWT, quindi la si ricava dalla richiesta senza passare dal database.
 */
@Entity
@Table(name = "chat_messaggio")
public class ChatMessaggio extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "username", nullable = false)
    public String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "autore", nullable = false)
    public AutoreChat autore;

    @Column(name = "testo", nullable = false)
    public String testo;

    @Column(name = "istante", nullable = false)
    public Instant istante;

    public static ChatMessaggio di(String username, AutoreChat autore, String testo) {
        ChatMessaggio messaggio = new ChatMessaggio();
        messaggio.username = username;
        messaggio.autore = autore;
        messaggio.testo = testo;
        messaggio.istante = Instant.now();
        return messaggio;
    }
}
