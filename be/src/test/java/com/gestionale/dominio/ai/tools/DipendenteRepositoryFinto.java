package com.gestionale.dominio.ai.tools;

import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.model.enums.FiltroStato;
import com.gestionale.dominio.repository.DipendenteRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Il repository sostituito con una lista in memoria.
 *
 * E' una sottoclasse e non un mock perche' in questo progetto i test non usano
 * Mockito: qui bastano sei metodi riscritti, e cosi' i test non portano dentro una
 * libreria in piu' solo per loro.
 *
 * I metodi originali non vengono mai eseguiti - e meno male, perche' senza Quarkus
 * avviato le query di Panache non funzionerebbero.
 */
class DipendenteRepositoryFinto extends DipendenteRepository {

    /** Tutti quelli "sul database", eliminati e scaduti compresi. */
    final List<Dipendente> tutti = new ArrayList<>();

    DipendenteRepositoryFinto conDipendente(Dipendente d) {
        tutti.add(d);
        return this;
    }

    @Override
    public PanacheQuery<Dipendente> elenco(FiltroStato stato) {
        return QueryFinta.di(filtra(stato));
    }

    @Override
    public long conta(FiltroStato stato) {
        return filtra(stato).size();
    }

    @Override
    public List<Dipendente> cercaTestuale(String testo, int max, FiltroStato stato) {
        String cercato = testo.toLowerCase();
        return filtra(stato).stream()
                .filter(d -> (d.nome + " " + d.cognome + " " + d.codiceFiscale).toLowerCase().contains(cercato))
                .limit(max)
                .toList();
    }

    @Override
    public Optional<Dipendente> perNominativo(String nominativo) {
        return tutti.stream()
                .filter(d -> (d.nome + " " + d.cognome).equalsIgnoreCase(nominativo.strip()))
                .findFirst();
    }

    @Override
    public PanacheQuery<Dipendente> inScadenza(LocalDate entro) {
        LocalDate oggi = LocalDate.now();
        return QueryFinta.di(tutti.stream()
                .filter(d -> d.sottoContrattoIl(oggi))
                .filter(d -> d.dataScadenza != null && !d.dataScadenza.isAfter(entro))
                .toList());
    }

    @Override
    public PanacheQuery<Dipendente> giaScaduti() {
        LocalDate oggi = LocalDate.now();
        return QueryFinta.di(tutti.stream()
                .filter(d -> !d.eliminato && d.dataScadenza != null && d.dataScadenza.isBefore(oggi))
                .toList());
    }

    private List<Dipendente> filtra(FiltroStato stato) {
        LocalDate oggi = LocalDate.now();
        return switch (stato) {
            case SOLO_ATTIVI -> tutti.stream().filter(d -> d.sottoContrattoIl(oggi)).toList();
            case ESCLUDI_ELIMINATI -> tutti.stream().filter(d -> !d.eliminato).toList();
            case TUTTI -> List.copyOf(tutti);
        };
    }
}
