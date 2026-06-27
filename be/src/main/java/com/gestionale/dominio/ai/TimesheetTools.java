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

@ApplicationScoped
public class TimesheetTools {

    @Inject DipendenteRepository dipendenteRepo;
    @Inject TimesheetRepository timesheetRepo;

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
        // Cerca il dipendente per nome+cognome (semplice match sul nominativo)
        Dipendente dip = trovaDipendentePerNome(nomeCompleto);
        if (dip == null) {
            return "Nessun dipendente trovato con nome '" + nomeCompleto + "'.";
        }

        BigDecimal totale = timesheetRepo.sommaOrePeriodo(dip.id, dataInizio, dataFine);
        return String.format("%s ha lavorato %s ore tra il %s e il %s.",
                nomeCompleto, totale, dataInizio, dataFine);
    }

    // Helper: cerca un dipendente confrontando "Nome Cognome"
    private Dipendente trovaDipendentePerNome(String nomeCompleto) {
        return dipendenteRepo.listAll().stream()
                .filter(d -> (d.nome + " " + d.cognome).equalsIgnoreCase(nomeCompleto.trim()))
                .findFirst()
                .orElse(null);
    }
}