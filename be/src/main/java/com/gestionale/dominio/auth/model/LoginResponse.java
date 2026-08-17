package com.gestionale.dominio.auth.model;

import java.util.Set;

public class LoginResponse {
    // Il token non lo mettiamo qui: sta solo nel cookie, che il JavaScript non puo' leggere
    public String username;

    // Serve al frontend per nascondere quello che l'utente non puo' fare.
    // Non e' un controllo di sicurezza: quello resta lato server.
    public Set<String> ruoli;

    public LoginResponse(String username, Set<String> ruoli) {
        this.username = username;
        this.ruoli = ruoli;
    }
}
