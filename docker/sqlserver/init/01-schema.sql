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
                               user_id   BIGINT NOT NULL,                       -- A quale utente appartiene il ruolo
                               role_name NVARCHAR(50) NOT NULL,                 -- Nome del ruolo (es. 'ADMIN', 'OPERATOR')
                               CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_name),  -- Chiave composta: stessa coppia utente+ruolo non si ripete
                               CONSTRAINT fk_user_role_user FOREIGN KEY (user_id)         -- Vincolo: user_id deve esistere in app_user
                                   REFERENCES app_user(id) ON DELETE CASCADE              -- Se cancelli l'utente, spariscono anche i suoi ruoli
);
GO

-- Inserisce l'utente 'admin' iniziale solo se non c'è già
IF NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'admin')  -- Controlla se 'admin' esiste già
BEGIN                                                            -- Inizio blocco di istruzioni multiple
INSERT INTO app_user (username, password, enabled)
VALUES ('admin', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5h0p9pYqXh6QrJ5QF5p5pXqQ9YQ9a', 1);
-- Inserisce admin con l'hash Bcrypt (placeholder, da rigenerare allo Step 6)

INSERT INTO app_user_role (user_id, role_name)
VALUES (SCOPE_IDENTITY(), 'ADMIN');      -- SCOPE_IDENTITY() = l'id appena generato dall'INSERT sopra; gli dà ruolo ADMIN
END
GO