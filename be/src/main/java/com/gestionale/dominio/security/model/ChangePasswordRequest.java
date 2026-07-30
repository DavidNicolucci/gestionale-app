package com.gestionale.dominio.security.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// La nuova password che l'admin imposta per un utente. Arriva in chiaro e viene
// cifrata (hash bcrypt) in UserService prima di finire nel database.
public class ChangePasswordRequest {

    @NotBlank(message = "Password obbligatoria")
    @Size(min = 8, message = "La password deve avere almeno 8 caratteri")
    public String password;
}
