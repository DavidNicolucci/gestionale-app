-- Crea il database 'gestionale' solo se non esiste già
IF DB_ID('gestionale') IS NULL        -- DB_ID restituisce l'id del DB, NULL se non esiste
    CREATE DATABASE gestionale;       -- Lo crea solo in quel caso (evita errori al secondo avvio)
GO                                    -- GO = separatore di batch in SQL Server: esegui quanto sopra prima di proseguire

USE gestionale;                       -- Da qui in poi tutti i comandi agiscono sul database 'gestionale'
GO

-- Tabella degli utenti applicativi (chi fa login)
IF OBJECT_ID('app_user', 'U') IS NULL -- OBJECT_ID con 'U' controlla se esiste una tabella (User table) con quel nome
CREATE TABLE app_user (
                          id          BIGINT IDENTITY(1,1) PRIMARY KEY,  -- ID numerico auto-incrementante (parte da 1, +1 ogni riga)
                          username    NVARCHAR(100) NOT NULL UNIQUE,      -- Nome utente; UNIQUE = non possono esistere due uguali
                          password    NVARCHAR(255) NOT NULL,             -- Hash Bcrypt della password (255 char per stare larghi)
                          enabled     BIT NOT NULL DEFAULT 1,             -- 1 = attivo, 0 = disabilitato; default attivo
                          created_at  DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()  -- Data creazione, valorizzata in automatico in UTC
);
GO

-- Tabella che collega ogni utente ai suoi ruoli (un utente può averne più di uno)
IF OBJECT_ID('app_user_role', 'U') IS NULL
CREATE TABLE app_user_role (
                               id        BIGINT IDENTITY(1,1) PRIMARY KEY,      -- PK surrogata semplice
                               user_id   BIGINT NOT NULL,
                               role_name NVARCHAR(50) NOT NULL,
                               CONSTRAINT uq_user_role UNIQUE (user_id, role_name),  -- la vecchia PK diventa vincolo di unicità
                               CONSTRAINT fk_user_role_user FOREIGN KEY (user_id)
                                   REFERENCES app_user(id) ON DELETE CASCADE
);
GO

-- Solo i ruoli che esistono davvero, cioe' quelli dei @RolesAllowed (enum Ruolo nel
-- backend). Un ruolo inventato darebbe un utente che entra ma riceve 403 ovunque.
-- Aggiungere un ruolo vuol dire aggiornare: @RolesAllowed, enum Ruolo e questo vincolo.
--
-- Perche' non basta "role_name IN ('ADMIN','OPERATOR')": il database confronta senza
-- badare alle maiuscole e ignorando gli spazi finali, quindi accetterebbe 'admin' e
-- 'ADMIN ', che per @RolesAllowed invece NON sono ADMIN. COLLATE Latin1_General_BIN2
-- rende il confronto esatto carattere per carattere; LIKE al posto di = perche' su
-- NVARCHAR, a differenza di =, tiene conto degli spazi finali.
--
-- ALTER separata e non dentro la CREATE: chi ha gia' il database non ripassa dalla
-- CREATE. Se nella tabella ci fosse gia' un ruolo non valido, l'ALTER fallisce e lo
-- dice: va corretto a mano prima di rilanciare lo script.
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_user_role_nome')
ALTER TABLE app_user_role ADD CONSTRAINT ck_user_role_nome CHECK (
       role_name COLLATE Latin1_General_BIN2 LIKE N'ADMIN'
    OR role_name COLLATE Latin1_General_BIN2 LIKE N'OPERATOR'
);
GO

-- Inserisce l'utente 'admin' iniziale solo se non c'è già
IF NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'admin')  -- Controlla se 'admin' esiste già
BEGIN                                                            -- Inizio blocco di istruzioni multiple
INSERT INTO app_user (username, password, enabled)
VALUES ('admin', '$2a$10$qntpYmaH4ZPeChKtscpl8OyRSHYszkv180TiJ1Nn3J1FEUHNmOdOq', 1);
-- Inserisce admin con l'hash Bcrypt reale della password 'admin123'

INSERT INTO app_user_role (user_id, role_name)
VALUES (SCOPE_IDENTITY(), 'ADMIN');      -- SCOPE_IDENTITY() = l'id appena generato dall'INSERT sopra; gli dà ruolo ADMIN
END
GO

-- ============================================================
-- TABELLE DI DOMINIO
-- ============================================================

-- ---- Dipendenti ----
IF OBJECT_ID('dipendente', 'U') IS NULL
CREATE TABLE dipendente (
                            id              BIGINT IDENTITY(1,1) PRIMARY KEY,
                            nome            NVARCHAR(100) NOT NULL,
                            cognome         NVARCHAR(100) NOT NULL,
                            codice_fiscale  NVARCHAR(16)  NOT NULL UNIQUE,   -- CF italiano: sempre 16 caratteri, univoco
                            data_nascita    DATE          NOT NULL,
                            nazionalita     NVARCHAR(60)  NOT NULL,
                            tipo_contratto  NVARCHAR(50)  NOT NULL,          -- es. 'INDETERMINATO', 'DETERMINATO'
                            data_assunzione DATE          NOT NULL,
                            data_scadenza   DATE          NULL               -- NULL se contratto a tempo indeterminato
);
GO

-- Colonna aggiunta dopo la creazione iniziale della tabella: chi ha gia' il database
-- non lo ricrea da zero, quindi la ALTER deve stare qui e non dentro la CREATE sopra.
-- COL_LENGTH torna NULL se la colonna non c'e': e' il modo di rendere la ALTER
-- ripetibile, come OBJECT_ID lo e' per le tabelle.
-- Cancellazione logica: il dipendente eliminato resta sul database perche' i suoi
-- timesheet lo referenziano, e le ore gia' consuntivate non devono sparire.
IF COL_LENGTH('dipendente', 'eliminato') IS NULL
    ALTER TABLE dipendente ADD eliminato BIT NOT NULL DEFAULT 0;   -- le righe esistenti diventano 0 = non eliminato
GO

-- ---- Clienti ----
IF OBJECT_ID('cliente', 'U') IS NULL
CREATE TABLE cliente (
                         id              BIGINT IDENTITY(1,1) PRIMARY KEY,
                         ragione_sociale NVARCHAR(200) NOT NULL,
                         partita_iva     NVARCHAR(20)  NULL,
                         indirizzo       NVARCHAR(250) NULL,             -- sede del cliente
                         eliminato       BIT NOT NULL DEFAULT 0          -- cancellazione logica
);
GO

-- ---- Siti (luoghi di lavoro associati a un cliente) ----
IF OBJECT_ID('sito', 'U') IS NULL
CREATE TABLE sito (
                      id          BIGINT IDENTITY(1,1) PRIMARY KEY,
                      nome        NVARCHAR(150) NOT NULL,              -- es. 'Cantiere Via Roma', 'Boutique Centro'
                      indirizzo   NVARCHAR(250) NULL,
                      cliente_id  BIGINT NOT NULL,                     -- FK verso cliente
                      eliminato   BIT NOT NULL DEFAULT 0,              -- cancellazione logica, come sul dipendente
                      CONSTRAINT fk_sito_cliente FOREIGN KEY (cliente_id)
                          REFERENCES cliente(id)                       -- NIENTE cascata: i siti non si cancellano da soli
);
GO

-- ---- Timesheet (ore lavorate) ----
IF OBJECT_ID('timesheet', 'U') IS NULL
CREATE TABLE timesheet (
                           id            BIGINT IDENTITY(1,1) PRIMARY KEY,
                           dipendente_id BIGINT NOT NULL,                   -- FK: chi ha lavorato
                           sito_id       BIGINT NOT NULL,                   -- FK: dove
                           data_lavoro   DATE          NOT NULL,            -- giorno della prestazione
                           ore_lavorate  DECIMAL(5,2)  NOT NULL,            -- es. 7.50 ore; DECIMAL evita errori di arrotondamento
                           note          NVARCHAR(500) NULL,
                           eliminato     BIT NOT NULL DEFAULT 0,            -- cancellazione logica: le ore restano sul database

                           CONSTRAINT fk_ts_dipendente FOREIGN KEY (dipendente_id)
                               REFERENCES dipendente(id),
                           CONSTRAINT fk_ts_sito FOREIGN KEY (sito_id)
                               REFERENCES sito(id)
);
GO

-- ============================================================
-- CANCELLAZIONE LOGICA su cliente, sito e timesheet
-- Le ALTER stanno qui e non dentro le CREATE TABLE sopra perche' chi ha gia' il
-- database non lo ricrea da zero. Stesso schema della colonna 'eliminato' del
-- dipendente: COL_LENGTH torna NULL se la colonna non c'e', quindi e' ripetibile.
-- ============================================================

IF COL_LENGTH('cliente', 'eliminato') IS NULL
    ALTER TABLE cliente ADD eliminato BIT NOT NULL DEFAULT 0;    -- le righe esistenti diventano 0 = non eliminato
GO

IF COL_LENGTH('sito', 'eliminato') IS NULL
    ALTER TABLE sito ADD eliminato BIT NOT NULL DEFAULT 0;
GO

IF COL_LENGTH('timesheet', 'eliminato') IS NULL
    ALTER TABLE timesheet ADD eliminato BIT NOT NULL DEFAULT 0;
GO

-- La cascata cliente -> sito va tolta dai database che ce l'hanno gia'.
-- Con la cancellazione logica una DELETE fisica sul cliente non parte piu', ma finche'
-- il vincolo resta com'e' basta una query lanciata a mano sul database per portarsi via
-- i siti in silenzio. delete_referential_action = 1 vuol dire CASCADE, 0 vuol dire
-- NO ACTION: il blocco non fa niente se la cascata e' gia' stata tolta.
IF EXISTS (SELECT 1 FROM sys.foreign_keys
           WHERE name = 'fk_sito_cliente' AND delete_referential_action = 1)
BEGIN
    ALTER TABLE sito DROP CONSTRAINT fk_sito_cliente;
    ALTER TABLE sito ADD CONSTRAINT fk_sito_cliente FOREIGN KEY (cliente_id)
        REFERENCES cliente(id);
END
GO

-- ---- Indici ----
-- Da quando ogni lettura porta con se' "AND eliminato = 0", la colonna entra negli
-- indici insieme alle FK. Sta subito dopo la colonna cercata per uguaglianza e prima
-- della data, perche' SQL Server usa un indice composto finche' trova uguaglianze:
-- (sito_id = X AND eliminato = 0) sono due uguaglianze, data_lavoro BETWEEN e' un
-- intervallo e va per ultimo.
--
-- Non li facciamo filtrati (WHERE eliminato = 0): sarebbero piu' piccoli, ma un indice
-- filtrato pretende QUOTED_IDENTIFIER ON su OGNI insert e update della tabella, e
-- sqlcmd di default ce l'ha OFF. Il seed qui sotto smetterebbe di funzionare.

-- L'indice del dipendente esisteva gia' senza 'eliminato': va rifatto, non aggiunto.
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_timesheet_dipendente')
   AND NOT EXISTS (SELECT 1 FROM sys.index_columns ic
                   JOIN sys.indexes i ON i.object_id = ic.object_id AND i.index_id = ic.index_id
                   JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                   WHERE i.name = 'ix_timesheet_dipendente' AND c.name = 'eliminato')
    DROP INDEX ix_timesheet_dipendente ON timesheet;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_timesheet_dipendente')
CREATE INDEX ix_timesheet_dipendente ON timesheet(dipendente_id, eliminato, data_lavoro);
GO

-- Questo non c'era: "quante ore sul cantiere di via Roma a luglio" e "chi ha lavorato
-- su questo sito" scorrevano tutta la tabella timesheet.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_timesheet_sito')
CREATE INDEX ix_timesheet_sito ON timesheet(sito_id, eliminato, data_lavoro);
GO

-- Serve al controllo "il cliente ha ancora siti attivi?" che blocca l'eliminazione,
-- e all'elenco dei siti di un cliente.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_sito_cliente')
CREATE INDEX ix_sito_cliente ON sito(cliente_id, eliminato);
GO

-- Su cliente e sito NON mettiamo un indice sulla sola colonna 'eliminato': quasi tutte
-- le righe valgono 0, quindi non separa niente e il motore lo ignorerebbe comunque.

-- ---- Chat con l'assistente AI ----
-- Storico integrale della conversazione, uno per utente: e' quello che il frontend
-- ricarica nella chatbox. La memoria che viene passata a Gemini e' un'altra cosa
-- (una finestra sugli ultimi messaggi, tenuta in RAM da langchain4j).
-- Le righe vengono cancellate al logout, vedi AuthResource.logout.
IF OBJECT_ID('chat_messaggio', 'U') IS NULL
CREATE TABLE chat_messaggio (
                                id       BIGINT IDENTITY(1,1) PRIMARY KEY,
                                username NVARCHAR(100)  NOT NULL,          -- chi ha la conversazione; stesso valore del JWT
                                autore   NVARCHAR(20)   NOT NULL,          -- 'UTENTE' oppure 'ASSISTENTE'
                                testo    NVARCHAR(MAX)  NOT NULL,          -- MAX: le risposte del modello possono essere lunghe
                                istante  DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME()
);
GO

-- La chat si legge sempre come "tutti i messaggi di un utente, in ordine"
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_chat_messaggio_utente')
CREATE INDEX ix_chat_messaggio_utente ON chat_messaggio(username, id);
GO

-- ============================================================
-- DATI DI ESEMPIO (seed) per poter testare l'import Excel
-- Ogni INSERT e' protetto da IF NOT EXISTS: rieseguibile senza duplicati.
-- I timesheet importati cercano il dipendente per CODICE FISCALE (colonna A)
-- e il sito per NOME (colonna B): questi valori devono combaciare con l'Excel.
-- ============================================================

-- ---- Cliente ----
IF NOT EXISTS (SELECT 1 FROM cliente WHERE ragione_sociale = 'Acme S.p.A.')
INSERT INTO cliente (ragione_sociale, partita_iva, indirizzo)
VALUES ('Acme S.p.A.', '01234567890', 'Via Milano 1, Milano');
GO

-- ---- Altri clienti ----
-- Servono per provare la paginazione della tabella: con Acme fanno 25 righe,
-- cioe' 5 pagine da 5, 3 da 10 e 1 da 25 (le dimensioni offerte dal paginatore).
-- Qui non usiamo 24 blocchi IF NOT EXISTS: la lista sta in una tabella di valori
-- e la NOT EXISTS scarta quelli gia' presenti, quindi resta rieseguibile come il resto.
INSERT INTO cliente (ragione_sociale, partita_iva, indirizzo)
SELECT nuovi.ragione_sociale, nuovi.partita_iva, nuovi.indirizzo
FROM (VALUES
    ('Bianchi Costruzioni S.r.l.',  '02345678901', 'Via Torino 22, Torino'),
    ('Rossi Impianti S.p.A.',       '03456789012', 'Corso Francia 8, Torino'),
    ('Verdi Logistica S.r.l.',      '04567890123', 'Via Emilia 45, Bologna'),
    ('Ferrari Meccanica S.p.A.',    '05678901234', 'Via del Lavoro 3, Modena'),
    ('Lombardi Servizi S.r.l.',     '06789012345', 'Piazza Duomo 12, Milano'),
    ('Marino Trasporti S.r.l.',     '07890123456', 'Via Napoli 90, Napoli'),
    ('Greco Edilizia S.p.A.',       '08901234567', 'Via Etnea 120, Catania'),
    ('Conti Energia S.r.l.',        '09012345678', 'Viale Marconi 7, Roma'),
    ('Ricci Informatica S.r.l.',    '10123456789', 'Via Tiburtina 220, Roma'),
    ('Bruno Alimentari S.p.A.',     '11234567890', 'Via Garibaldi 15, Parma'),
    ('Galli Arredamenti S.r.l.',    '12345678901', 'Via Veneto 33, Firenze'),
    ('Costa Navale S.p.A.',         '13456789012', 'Molo Ponente 2, Genova'),
    ('Fontana Chimica S.r.l.',      '14567890123', 'Zona Industriale 18, Ravenna'),
    ('Barbieri Tessile S.p.A.',     '15678901234', 'Via delle Filande 6, Prato'),
    ('Moretti Vini S.r.l.',         '16789012345', 'Strada Provinciale 4, Asti'),
    ('Sartori Farmaceutici S.p.A.', '17890123456', 'Via Pasteur 11, Verona'),
    ('De Luca Sicurezza S.r.l.',    '18901234567', 'Via Cavour 77, Bari'),
    ('Pellegrini Catering S.r.l.',  '19012345678', 'Via Appia 200, Latina'),
    ('Villa Immobiliare S.p.A.',    '20123456789', 'Corso Vittorio 5, Milano'),
    ('Gatti Elettronica S.r.l.',    '21234567890', 'Via Fermi 9, Pavia'),
    ('Rizzo Agricola S.r.l.',       '22345678901', 'Contrada Piana 30, Ragusa'),
    ('Amato Metalli S.p.A.',        '23456789012', 'Via Aldo Moro 14, Brescia'),
    ('Silvestri Grafica S.r.l.',    '24567890123', 'Via Gutenberg 2, Padova'),
    ('Palumbo Marittima S.p.A.',    '25678901234', 'Banchina Levante 1, Trieste')
) AS nuovi(ragione_sociale, partita_iva, indirizzo)
WHERE NOT EXISTS (
    SELECT 1 FROM cliente c WHERE c.ragione_sociale = nuovi.ragione_sociale
);
GO

-- ---- Siti (associati al cliente Acme) ----
IF NOT EXISTS (SELECT 1 FROM sito WHERE nome = 'Cantiere Via Roma')
INSERT INTO sito (nome, indirizzo, cliente_id)
VALUES ('Cantiere Via Roma', 'Via Roma 10, Milano',
        (SELECT id FROM cliente WHERE ragione_sociale = 'Acme S.p.A.'));
GO

IF NOT EXISTS (SELECT 1 FROM sito WHERE nome = 'Boutique Centro')
INSERT INTO sito (nome, indirizzo, cliente_id)
VALUES ('Boutique Centro', 'Corso Buenos Aires 5, Milano',
        (SELECT id FROM cliente WHERE ragione_sociale = 'Acme S.p.A.'));
GO

-- ---- Dipendenti ----
IF NOT EXISTS (SELECT 1 FROM dipendente WHERE codice_fiscale = 'RSSMRA85M01H501Z')
INSERT INTO dipendente (nome, cognome, codice_fiscale, data_nascita, nazionalita, tipo_contratto, data_assunzione, data_scadenza)
VALUES ('Mario', 'Rossi', 'RSSMRA85M01H501Z', '1985-08-01', 'Italiana', 'INDETERMINATO', '2020-01-15', NULL);
GO

IF NOT EXISTS (SELECT 1 FROM dipendente WHERE codice_fiscale = 'BNCLGU90A01F205W')
INSERT INTO dipendente (nome, cognome, codice_fiscale, data_nascita, nazionalita, tipo_contratto, data_assunzione, data_scadenza)
VALUES ('Luigi', 'Bianchi', 'BNCLGU90A01F205W', '1990-01-01', 'Italiana', 'DETERMINATO', '2023-03-01', '2026-03-01');
GO