package com.gestionale.dominio.security.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

// Dati che l'admin invia per creare un nuovo utente.
// La password arriva IN CHIARO: viene cifrata (hash bcrypt) dentro UserService,
// non viene mai salvata cosi' com'e' e non viene mai rimandata indietro.
public class CreateUserRequest {

    @NotBlank(message = "Username obbligatorio")
    public String username;

    @NotBlank(message = "Password obbligatoria")
    @Size(min = 8, message = "La password deve avere almeno 8 caratteri")
    public String password;

    // Ruoli da assegnare (es. ["USER"] oppure ["ADMIN"]). Se la lista arriva
    // vuota o null, UserService assegna il ruolo di base "USER".
    public List<String> ruoli;
}
