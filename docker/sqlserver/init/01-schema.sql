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

-- ---- Clienti ----
IF OBJECT_ID('cliente', 'U') IS NULL
CREATE TABLE cliente (
                         id              BIGINT IDENTITY(1,1) PRIMARY KEY,
                         ragione_sociale NVARCHAR(200) NOT NULL,
                         partita_iva     NVARCHAR(20)  NULL,
                         indirizzo       NVARCHAR(250) NULL              -- sede del cliente
);
GO

-- ---- Siti (luoghi di lavoro associati a un cliente) ----
IF OBJECT_ID('sito', 'U') IS NULL
CREATE TABLE sito (
                      id          BIGINT IDENTITY(1,1) PRIMARY KEY,
                      nome        NVARCHAR(150) NOT NULL,              -- es. 'Cantiere Via Roma', 'Boutique Centro'
                      indirizzo   NVARCHAR(250) NULL,
                      cliente_id  BIGINT NOT NULL,                     -- FK verso cliente
                      CONSTRAINT fk_sito_cliente FOREIGN KEY (cliente_id)
                          REFERENCES cliente(id) ON DELETE CASCADE     -- elimino il cliente -> spariscono i suoi siti
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
                           CONSTRAINT fk_ts_dipendente FOREIGN KEY (dipendente_id)
                               REFERENCES dipendente(id),
                           CONSTRAINT fk_ts_sito FOREIGN KEY (sito_id)
                               REFERENCES sito(id)
);
GO

-- Indici sulle FK più interrogate (le query "ore di X" filtrano per dipendente e data)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_timesheet_dipendente')
CREATE INDEX ix_timesheet_dipendente ON timesheet(dipendente_id, data_lavoro);
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