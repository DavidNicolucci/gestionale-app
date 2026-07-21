package com.gestionale.dominio.ai;

import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.repository.DipendenteRepository;
import com.gestionale.dominio.repository.TimesheetRepository;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TimesheetTools {

    private final DipendenteRepository dipendenteRepo;
    private final TimesheetRepository timesheetRepo;

    @Inject
    public TimesheetTools(DipendenteRepository dipendenteRepo, TimesheetRepository timesheetRepo) {
        this.dipendenteRepo = dipendenteRepo;
        this.timesheetRepo = timesheetRepo;
    }

    @Tool("Restituisce l'elenco di tutti i dipendenti registrati, con nome e cognome. " +
            "Usalo quando l'utente chiede chi sono i dipendenti o vuole conoscere i nominativi.")
    public List<String> elencaDipendenti() {
        return dipendenteRepo.listAll().stream()
                .map(d -> d.nome + " " + d.cognome + " (CF: " + d.codiceFiscale + ")")
                .toList();
    }

    @Tool("Calcola il totale delle ore lavorate da un dipendente in un intervallo di date. " +
            "I parametri sono: il nome e cognome del dipendente, la data di inizio e la data di fine " +
            "del periodo, entrambe nel formato AAAA-MM-GG. " +
            "Usalo quando l'utente chiede quante ore ha lavorato una persona in un certo periodo o mese.")
    public String oreLavorate(String nomeCompleto, LocalDate dataInizio, LocalDate dataFine) {
        // La ricerca per nome la fa il database, non carichiamo tutti i dipendenti
        // per poi filtrarli in Java.
        Optional<Dipendente> dip = dipendenteRepo.perNominativo(nomeCompleto);

        if (dip.isEmpty()) {
            // Torniamo una frase e non un errore, perche' questo testo lo legge l'AI:
            // cosi' risponde che il dipendente non c'e' invece di inventarsi i dati.
            return "Nessun dipendente trovato con nome '" + nomeCompleto + "'.";
        }

        BigDecimal totale = timesheetRepo.sommaOrePeriodo(dip.get().id, dataInizio, dataFine);
        return String.format("%s ha lavorato %s ore tra il %s e il %s.",
                nomeCompleto, totale, dataInizio, dataFine);
    }
}
