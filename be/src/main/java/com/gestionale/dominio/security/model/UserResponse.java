package com.gestionale.dominio.security.model;

import java.util.List;

// Cosa restituiamo dopo aver creato un utente. Volutamente NON contiene la password
// (nemmeno l'hash): all'esterno non deve mai uscire.
public class UserResponse {
    public Long id;
    public String username;
    public boolean enabled;
    public List<String> ruoli;

    public UserResponse(Long id, String username, boolean enabled, List<String> ruoli) {
        this.id = id;
        this.username = username;
        this.enabled = enabled;
        this.ruoli = ruoli;
    }
}
