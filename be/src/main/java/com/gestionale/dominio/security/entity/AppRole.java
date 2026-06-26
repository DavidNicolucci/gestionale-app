package com.gestionale.dominio.security.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.security.jpa.RolesValue;
import jakarta.persistence.*;

@Entity
@Table(name = "app_user_role")
public class AppRole extends PanacheEntityBase {

    // La tabella ha PK composta (user_id, role_name). Per semplicità di mapping
    // usiamo role_name come campo dei ruoli letto da security-jpa.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "role_name", nullable = false)
    @RolesValue                       // security-jpa: il valore del ruolo (es. "ADMIN")
    public String roleName;
}