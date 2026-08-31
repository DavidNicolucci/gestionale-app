package com.gestionale.dominio.ai.tools;

import com.gestionale.dominio.model.entity.Cliente;
import com.gestionale.dominio.model.entity.Dipendente;
import com.gestionale.dominio.model.entity.Sito;
import com.gestionale.dominio.model.entity.Timesheet;
import com.gestionale.dominio.repository.ClienteRepository;
import com.gestionale.dominio.repository.DipendenteRepository;
import com.gestionale.dominio.repository.OrePeriodo;
import com.gestionale.dominio.repository.SitoRepository;
import com.gestionale.dominio.repository.TimesheetRepository;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Le ore lavorate: per dipendente, per sito, per cliente.
 *
 * Le somme le fa sempre il database. Caricare le righe e sommarle qui sarebbe
 * gia' uno spreco di suo, ma soprattutto quelle righe finirebbero nel prompt del
 * modello: su un mese di consuntivi sarebbero migliaia. Sono tutte letture.
 */
@ApplicationScoped
public class TimesheetTools {

    @Inject
    DipendenteRepository dipendenti;

    @Inject
    SitoRepository siti;

    @Inject
    ClienteRepository clienti;

    @Inject
    TimesheetRepository timesheet;

    @Tool("""
            Calcola il totale delle ore lavorate da un dipendente in un intervallo di date.
            I parametri sono: nome e cognome del dipendente, data di inizio e data di fine
            del periodo, entrambe nel formato AAAA-MM-GG.
            Usalo quando l'utente chiede quante ore ha lavorato una persona in un certo periodo o mese.
            Le due date che trovi nella risposta sono il primo e l'ultimo giorno in cui la
            persona ha effettivamente lavorato, non gli estremi del periodo che hai chiesto:
            riportale come sono, senza sostituirle con l'inizio e la fine del mese.
            """)
    public String oreLavorate(String nomeCompleto, LocalDate dataInizio, LocalDate dataFine) {
        // La ricerca per nome la fa il database, non carichiamo tutti i dipendenti
        // per poi filtrarli in Java.
        Optional<Dipendente> dip = dipendenti.perNominativo(nomeCompleto);

        if (dip.isEmpty()) {
            // Torniamo una frase e non un errore, perche' questo testo lo legge l'AI:
            // cosi' risponde che il dipendente non c'e' invece di inventarsi i dati.
            return "Nessun dipendente trovato con nome '" + nomeCompleto + "'.";
        }

        OrePeriodo lavorate = timesheet.oreLavoratePeriodo(dip.get().id, dataInizio, dataFine);

        // Senza righe non esiste un primo e un ultimo giorno da riportare: qui le
        // uniche date sensate sono quelle chieste, ed e' l'unico caso in cui vanno
        // ripetute all'utente.
        if (lavorate.vuoto()) {
            return "%s non ha ore registrate tra il %s e il %s.".formatted(
                    nomeCompleto, TestoTools.data(dataInizio), TestoTools.data(dataFine));
        }

        // Gli estremi sono quelli delle righe trovate, non quelli del periodo chiesto:
        // a chi domanda "quante ore ad agosto" interessa sapere che ha lavorato dal 24
        // al 27, e dire "dall'1 al 31" farebbe sembrare vuoto il resto del mese quando
        // magari la persona era assunta solo da meta' mese.
        return "%s ha lavorato %s ore tra il %s e il %s.".formatted(
                nomeCompleto, TestoTools.ore(lavorate.ore()),
                TestoTools.data(lavorate.primoGiorno()), TestoTools.data(lavorate.ultimoGiorno()));
    }

    @Tool("""
            Calcola il totale delle ore lavorate su un sito (cantiere, negozio, sede) in un periodo.
            I parametri sono il nome del sito e le due date nel formato AAAA-MM-GG.
            Usalo per domande come "quante ore sono state fatte sul cantiere di via Roma a luglio".
            Le due date che trovi nella risposta sono il primo e l'ultimo giorno in cui si e'
            davvero lavorato sul sito, non gli estremi del periodo che hai chiesto: riportale
            come sono, senza sostituirle con l'inizio e la fine del mese.
            """)
    public String oreDelSito(String nomeSito, LocalDate dataInizio, LocalDate dataFine) {
        Optional<Sito> sito = siti.perNome(nomeSito);

        if (sito.isEmpty()) {
            return "Nessun sito trovato con nome '" + nomeSito
                    + "'. Prova cercaSiti per trovare il nome esatto.";
        }

        OrePeriodo lavorate = timesheet.oreSitoPeriodo(sito.get().id, dataInizio, dataFine);

        if (lavorate.vuoto()) {
            return "Sul sito %s non ci sono ore registrate tra il %s e il %s.".formatted(
                    sito.get().nome, TestoTools.data(dataInizio), TestoTools.data(dataFine));
        }

        return "Sul sito %s sono state lavorate %s ore tra il %s e il %s.".formatted(
                sito.get().nome, TestoTools.ore(lavorate.ore()),
                TestoTools.data(lavorate.primoGiorno()), TestoTools.data(lavorate.ultimoGiorno()));
    }

    @Tool("""
            Calcola il totale delle ore lavorate per un cliente in un periodo, sommando tutti i suoi siti.
            I parametri sono la ragione sociale del cliente e le due date nel formato AAAA-MM-GG.
            Usalo per domande come "quante ore abbiamo fatto per Acme quest'anno".
            Le due date che trovi nella risposta sono il primo e l'ultimo giorno in cui si e'
            davvero lavorato per quel cliente, non gli estremi del periodo che hai chiesto:
            riportale come sono, senza sostituirle con l'inizio e la fine del mese.
            """)
    public String oreDelCliente(String ragioneSociale, LocalDate dataInizio, LocalDate dataFine) {
        Optional<Cliente> cliente = clienti.perRagioneSociale(ragioneSociale);

        if (cliente.isEmpty()) {
            return "Nessun cliente trovato con ragione sociale '" + ragioneSociale
                    + "'. Prova cercaClienti per trovare il nome esatto.";
        }

        OrePeriodo lavorate = timesheet.oreClientePeriodo(cliente.get().id, dataInizio, dataFine);

        if (lavorate.vuoto()) {
            return "Per il cliente %s non ci sono ore registrate tra il %s e il %s.".formatted(
                    cliente.get().ragioneSociale,
                    TestoTools.data(dataInizio), TestoTools.data(dataFine));
        }

        return "Per il cliente %s sono state lavorate %s ore tra il %s e il %s.".formatted(
                cliente.get().ragioneSociale, TestoTools.ore(lavorate.ore()),
                TestoTools.data(lavorate.primoGiorno()), TestoTools.data(lavorate.ultimoGiorno()));
    }

    @Tool("""
            Elenca chi ha lavorato su un sito in un periodo e quante ore ha fatto ciascuno,
            dal dipendente con piu' ore al dipendente con meno ore.
            I parametri sono il nome del sito e le due date nel formato AAAA-MM-GG.
            Usalo per domande come "chi ha lavorato sul cantiere di via Roma la settimana scorsa".
            """)
    public String chiHaLavoratoSulSito(String nomeSito, LocalDate dataInizio, LocalDate dataFine) {
        Optional<Sito> sito = siti.perNome(nomeSito);

        if (sito.isEmpty()) {
            return "Nessun sito trovato con nome '" + nomeSito
                    + "'. Prova cercaSiti per trovare il nome esatto.";
        }

        return TestoTools.riepilogo(
                timesheet.orePerDipendenteSuSito(sito.get().id, dataInizio, dataFine),
                "Nessuna ora registrata sul sito " + sito.get().nome + " nel periodo indicato.");
    }

    @Tool("""
            Riepiloga le ore lavorate da tutti i dipendenti in un periodo, dal piu' al meno ore,
            con il totale complessivo. I parametri sono le due date nel formato AAAA-MM-GG.
            Usalo per confronti e classifiche, per esempio "chi ha lavorato di piu' questo mese"
            o "quante ore sono state fatte in totale a luglio".
            """)
    public String riepilogoOrePerDipendente(LocalDate dataInizio, LocalDate dataFine) {
        return TestoTools.riepilogo(
                timesheet.orePerDipendente(dataInizio, dataFine),
                "Nessuna ora registrata tra il " + TestoTools.data(dataInizio)
                        + " e il " + TestoTools.data(dataFine) + ".");
    }

    @Tool("""
            Riepiloga le ore lavorate divise per cliente in un periodo, dal cliente con piu' ore,
            con il totale complessivo. I parametri sono le due date nel formato AAAA-MM-GG.
            Usalo per domande come "su quale cliente abbiamo lavorato di piu' quest'anno".
            """)
    public String riepilogoOrePerCliente(LocalDate dataInizio, LocalDate dataFine) {
        return TestoTools.riepilogo(
                timesheet.orePerCliente(dataInizio, dataFine),
                "Nessuna ora registrata tra il " + TestoTools.data(dataInizio)
                        + " e il " + TestoTools.data(dataFine) + ".");
    }

    @Tool("""
            Mostra le ultime registrazioni di ore inserite, dalla piu' recente, con dipendente,
            sito, data e ore. Il parametro e' quante registrazioni mostrare.
            Usalo per domande come "ultime ore inserite" o "cosa e' stato registrato di recente".
            """)
    public String ultimeRegistrazioni(int quante) {
        // Un numero assurdo (o negativo, se il modello sbaglia) riempirebbe il
        // prompt o farebbe fallire la query: lo teniamo nei limiti.
        int limite = Math.clamp(quante, 1, TestoTools.MAX_RIGHE);

        return TestoTools.elenco(timesheet.ultimeRegistrazioni(limite),
                this::riga,
                "Nessuna registrazione di ore presente.");
    }

    private String riga(Timesheet t) {
        return "%s - %s %s - sito %s - %s ore".formatted(
                TestoTools.data(t.dataLavoro),
                t.dipendente.nome, t.dipendente.cognome,
                t.sito.nome,
                TestoTools.ore(t.oreLavorate));
    }
}
