package com.gestionale.dominio.imports;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Un file caricato e il suo destino.
 *
 * La riga nasce nell'endpoint di upload (stato ACCODATO) e viene aggiornata dal
 * consumer man mano che il file viene elaborato. E' l'unica traccia durevole di un
 * import: la coda dimentica il messaggio appena viene consumato, le metriche contano
 * ma non dicono QUALE file, e i log scorrono.
 */
@Entity
@Table(name = "import_job")
public class ImportJob extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "file_name", nullable = false)
    public String fileName;

    /** Percorso su disco. Finche' il file c'e', l'import si puo' rilanciare. */
    @Column(name = "file_path", nullable = false)
    public String filePath;

    /** SHA-256 del contenuto: e' la risposta a "questo file l'abbiamo gia' importato?". */
    @Column(name = "file_hash", nullable = false)
    public String fileHash;

    // Come stringa e non come numero: sul database si deve capire cosa c'e' scritto,
    // e un ordinale cambierebbe significato al primo riordino dell'enum.
    @Enumerated(EnumType.STRING)
    @Column(name = "stato", nullable = false)
    public StatoImport stato;

    /** Quante volte il consumer ha preso in carico questo file, rilanci compresi. */
    @Column(name = "tentativi", nullable = false)
    public int tentativi;

    @Column(name = "righe_inserite", nullable = false)
    public int righeInserite;

    /** Righe che esistevano gia' (stesso dipendente, sito e giorno): ore sovrascritte. */
    @Column(name = "righe_aggiornate", nullable = false)
    public int righeAggiornate;

    @Column(name = "righe_scartate", nullable = false)
    public int righeScartate;

    /**
     * Numero dell'ultima riga del foglio gia' salvata sul database.
     *
     * Esiste perche' l'import non e' piu' una transazione sola: le righe vengono
     * salvate a blocchi, e ogni blocco committato aggiorna questo numero. Se il file
     * si ferma alla riga 4.999, le prime 4.998 sono a database davvero e il rilancio
     * riparte da li' invece che da capo.
     */
    @Column(name = "ultima_riga", nullable = false)
    public int ultimaRiga;

    /** Perche' e' fallito, in forma leggibile: e' la colonna che si guarda nell'elenco. */
    @Column(name = "errore")
    public String errore;

    @Column(name = "caricato_da")
    public String caricatoDa;

    @Column(name = "creato_il", nullable = false)
    public Instant creatoIl;

    @Column(name = "aggiornato_il", nullable = false)
    public Instant aggiornatoIl;

    /** Il rilancio ha senso solo se il file da rileggere e' ancora al suo posto. */
    public boolean fileDisponibile() {
        return filePath != null && java.nio.file.Files.exists(java.nio.file.Paths.get(filePath));
    }

    public void cambiaStato(StatoImport nuovo) {
        this.stato = nuovo;
        this.aggiornatoIl = Instant.now();
    }
}
