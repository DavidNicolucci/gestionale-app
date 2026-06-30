package com.gestionale.dominio.auth.model;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Username obbligatorio")
    public String username;

    @NotBlank(message = "Password obbligatoria")
    public String password;
}