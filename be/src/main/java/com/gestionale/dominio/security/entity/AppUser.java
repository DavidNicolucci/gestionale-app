package com.gestionale.dominio.security.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "app_user")
@UserDefinition                       // <-- security-jpa: questa è L'entity utente per l'auth
public class AppUser extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Username                         // <-- campo username
    @Column(name = "username")
    public String username;

    @Password                         // <-- campo password (di default: hash bcrypt MCF)
    @Column(name = "password")
    public String password;

    // La colonna esisteva gia' nello schema (BIT NOT NULL DEFAULT 1) ma non era mappata:
    // il login non la controllava, quindi un utente disabilitato riusciva ad autenticarsi.
    @Column(name = "enabled", nullable = false)
    public boolean enabled;

    @Roles                            // <-- collega ai ruoli via la relazione
    @OneToMany
    @JoinColumn(name = "user_id")     // FK in app_user_role che punta a questo utente
    public List<AppRole> roles;
}