package com.gestionale.dominio.auth.model;

public class LoginResponse {
    // Il token non lo mettiamo qui: sta solo nel cookie, che il JavaScript non puo' leggere
    public String username;

    public LoginResponse(String username) {
        this.username = username;
    }
}
