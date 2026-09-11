package com.gestionale.dominio.model.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// Campi per la modifica di un dipendente (PATCH): si mandano solo quelli da cambiare.
// Un campo che non arriva resta com'e', quindi qui niente campi obbligatori.
// L'id non sta qui: e' gia' nell'indirizzo della chiamata (/api/dipendenti/{id}).
//
// I controlli tipo @Size, @Past e @Pattern non scattano sui campi null: valgono solo
// per i campi che arrivano davvero.
public class DipendentePatchRequest {

    // La regex vuole almeno un carattere che non sia uno spazio: se mandi il campo,
    // non puoi svuotarlo.
    @Pattern(regexp = ".*\\S.*", message = "Il nome non può essere vuoto")
    public String nome;

    @Pattern(regexp = ".*\\S.*", message = "Il cognome non può essere vuoto")
    public String cognome;

    @Size(min = 16, max = 16, message = "Il codice fiscale deve essere di 16 caratteri")
    public String codiceFiscale;

    @Past(message = "La data di nascita deve essere nel passato")
    public LocalDate dataNascita;

    @Pattern(regexp = ".*\\S.*", message = "La nazionalità non può essere vuota")
    public String nazionalita;

    @Pattern(regexp = ".*\\S.*", message = "Il tipo di contratto non può essere vuoto")
    public String tipoContratto;

    public LocalDate dataAssunzione;

    public LocalDate dataScadenza;

    // Azzera la scadenza, cioe' porta il contratto a tempo indeterminato.
    //
    // Serve un campo apposta perche' in una PATCH "campo assente" e "campo a null"
    // arrivano uguali: sono tutti e due null, e la regola di tutti gli altri campi e'
    // "null vuol dire non toccarlo". Senza questo, un contratto a termine non potrebbe
    // piu' diventare indeterminato: resterebbe con addosso la vecchia data di scadenza,
    // e il dipendente continuerebbe a risultare in scadenza o scaduto pur non avendo
    // piu' un termine.
    //
    // Primitivo e non Boolean: se il client non manda il campo resta false, che e' il
    // comportamento di prima.
    public boolean rimuoviScadenza;
}
