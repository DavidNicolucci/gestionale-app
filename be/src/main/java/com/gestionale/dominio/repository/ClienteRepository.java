package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.entity.Cliente;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped     // bean CDI singleton, iniettabile nei service
public class ClienteRepository implements PanacheRepository<Cliente> {

    // PanacheRepository fornisce GIÀ: persist, findById, listAll, delete, count...
    // Qui aggiungiamo solo le query specifiche del dominio.


}