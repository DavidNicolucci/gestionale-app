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
@UserDefinition                       // dice a Quarkus che gli utenti del login stanno qui
public class AppUser extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Username                         // questo e' il campo dell'username
    @Column(name = "username")
    public String username;

    @Password                         // questo e' il campo della password, salvata cifrata
    @Column(name = "password")
    public String password;

    // La colonna c'era gia' nel database ma non l'avevamo mappata qui, cosi' il login
    // non la guardava e anche gli utenti disattivati riuscivano a entrare.
    @Column(name = "enabled", nullable = false)
    public boolean enabled;

    @Roles                            // qui ci sono i ruoli dell'utente
    @OneToMany
    @JoinColumn(name = "user_id")     // colonna user_id nella tabella app_user_role
    public List<AppRole> roles;
}