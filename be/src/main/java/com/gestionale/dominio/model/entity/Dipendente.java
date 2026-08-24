package com.gestionale.dominio.model.entity;

import com.gestionale.dominio.model.enums.StatoDipendente;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "dipendente")
public class Dipendente extends PanacheEntityBase {

    // Quanti giorni prima della scadenza il contratto va segnalato. Sta qui, vicino
    // alle regole che lo usano, e non sparso fra service e frontend: il giorno che
    // diventa 30 si cambia in un punto solo. Se un domani deve variare per ambiente
    // diventa una property, ma finche' e' una regola fissa una costante basta.
    public static final int GIORNI_PREAVVISO = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // l'id lo assegna il database
    public Long id;

    @Column(name = "nome", nullable = false)
    public String nome;

    @Column(name = "cognome", nullable = false)
    public String cognome;

    @Column(name = "codice_fiscale", nullable = false)
    public String codiceFiscale;

    @Column(name = "data_nascita", nullable = false)
    public LocalDate dataNascita;

    @Column(name = "nazionalita", nullable = false)
    public String nazionalita;

    @Column(name = "tipo_contratto", nullable = false)
    public String tipoContratto;

    @Column(name = "data_assunzione", nullable = false)
    public LocalDate dataAssunzione;

    @Column(name = "data_scadenza")
    public LocalDate dataScadenza;     // vuota se il contratto e' a tempo indeterminato

    // Cancellazione logica: la riga resta sul database. Serve perche' i timesheet
    // puntano al dipendente, e una cancellazione fisica si porterebbe dietro le ore
    // gia' consuntivate. boolean e non Boolean: sul database la colonna e' NOT NULL,
    // quindi il valore "non lo so" non esiste.
    @Column(name = "eliminato", nullable = false)
    public boolean eliminato;

    // Il dipendente era assumibile in quella data? E' la domanda che fanno i timesheet,
    // e la data da passare e' quella del lavoro svolto, non oggi: registrare a novembre
    // le ore di ottobre e' normale, e un contratto finito il 31 ottobre quelle ore le
    // copriva. Scadenza inclusiva: l'ultimo giorno di contratto si lavora ancora.
    public boolean sottoContrattoIl(LocalDate data) {
        return !eliminato
                && !data.isBefore(dataAssunzione)
                && (dataScadenza == null || !data.isAfter(dataScadenza));
    }

    // Lo stato da mostrare. Prende "oggi" come parametro invece di chiamare
    // LocalDate.now() qui dentro: cosi' chi lo usa decide la data di riferimento
    // e il metodo si puo' provare senza aspettare che passi la mezzanotte.
    public StatoDipendente stato(LocalDate oggi) {
        if (eliminato) {
            return StatoDipendente.ELIMINATO;
        }
        if (dataScadenza == null) {
            return StatoDipendente.ATTIVO;      // tempo indeterminato: non scade mai
        }
        if (dataScadenza.isBefore(oggi)) {
            return StatoDipendente.SCADUTO;
        }
        // Da qui la scadenza e' oggi o nel futuro: e' "in scadenza" se cade dentro
        // la finestra di preavviso, altrimenti e' un attivo qualsiasi.
        return dataScadenza.isAfter(oggi.plusDays(GIORNI_PREAVVISO))
                ? StatoDipendente.ATTIVO
                : StatoDipendente.IN_SCADENZA;
    }
}
