package com.gestionale.dominio.auth.model;

public class LoginResponse {
    public String token;
    public String username;
    public String tipo = "Bearer";   // tipo di token, standard

    public LoginResponse(String token, String username) {
        this.token = token;
        this.username = username;
    }
}