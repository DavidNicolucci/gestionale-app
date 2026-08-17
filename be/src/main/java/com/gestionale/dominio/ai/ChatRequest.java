package com.gestionale.dominio.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** La domanda dell'utente. */
public class ChatRequest {

    /** Il limite serve a non spedire a Gemini (a pagamento) testi incollati per sbaglio. */
    @NotBlank(message = "La domanda non puo' essere vuota")
    @Size(max = 2000, message = "La domanda non puo' superare i 2000 caratteri")
    public String domanda;
}
