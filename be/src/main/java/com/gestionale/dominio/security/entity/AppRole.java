package com.gestionale.dominio.security.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.security.jpa.RolesValue;
import jakarta.persistence.*;

@Entity
@Table(name = "app_user_role")
public class AppRole extends PanacheEntityBase {

    // Nel database la chiave e' la coppia (user_id, role_name), ma mapparla cosi'
    // e' piu' semplice e a noi basta leggere il nome del ruolo.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "role_name", nullable = false)
    @RolesValue                       // il nome del ruolo, es. "ADMIN"
    public String roleName;
}