package com.gestionale.dominio.auth.model;

public class LoginResponse {
    // Il token NON viene piu' esposto nel body: viaggia solo nel cookie HttpOnly
    public String username;

    public LoginResponse(String username) {
        this.username = username;
    }
}
