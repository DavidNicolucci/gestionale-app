package com.gestionale.dominio.ai;

import java.time.Instant;

/** Un messaggio come lo legge il frontend. */
public class ChatMessaggioResponse {
    public Long id;
    public AutoreChat autore;
    public String testo;
    public Instant istante;

    public static ChatMessaggioResponse da(ChatMessaggio messaggio) {
        ChatMessaggioResponse risposta = new ChatMessaggioResponse();
        risposta.id = messaggio.id;
        risposta.autore = messaggio.autore;
        risposta.testo = messaggio.testo;
        risposta.istante = messaggio.istante;
        return risposta;
    }
}
