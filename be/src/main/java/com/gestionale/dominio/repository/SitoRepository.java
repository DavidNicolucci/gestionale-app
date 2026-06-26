
package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.entity.Sito;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class SitoRepository implements PanacheRepository<Sito> {

    public List<Sito> perCliente(Long clienteId) {
        return list("cliente.id", clienteId);
    }

    public Sito perNome(String nome) {
        return find("nome", nome).firstResult();
    }
}