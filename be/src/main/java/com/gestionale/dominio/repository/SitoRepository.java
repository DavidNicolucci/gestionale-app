
package com.gestionale.dominio.repository;

import com.gestionale.dominio.model.entity.Sito;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SitoRepository implements PanacheRepository<Sito> {

    public List<Sito> perCliente(Long clienteId) {
        return list("cliente.id", clienteId);
    }

    // Optional: il sito cercato per nome puo' non esistere (es. un nome sbagliato
    // nell'Excel di import). La firma lo dichiara, invece di restituire null.
    public Optional<Sito> perNome(String nome) {
        return find("nome", nome).firstResultOptional();
    }
}