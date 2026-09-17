-- Dati di partenza dei test che avviano l'applicazione (@QuarkusTest).
-- Hibernate esegue questo file dopo aver creato lo schema dalle entity, sul SQL Server
-- usa-e-getta di Dev Services: non tocca ne' il database di sviluppo ne' lo script
-- docker/sqlserver/init/01-schema.sql, che resta quello vero.
--
-- Una istruzione per riga: e' come il file viene letto.
-- L'hash e' quello di 'admin123', lo stesso dell'utente di bootstrap in sviluppo.

INSERT INTO app_user (username, password, enabled, token_epoch) VALUES ('admin', '$2a$10$qntpYmaH4ZPeChKtscpl8OyRSHYszkv180TiJ1Nn3J1FEUHNmOdOq', 1, 0);
INSERT INTO app_user (username, password, enabled, token_epoch) VALUES ('operatore', '$2a$10$qntpYmaH4ZPeChKtscpl8OyRSHYszkv180TiJ1Nn3J1FEUHNmOdOq', 1, 0);
INSERT INTO app_user (username, password, enabled, token_epoch) VALUES ('sospeso', '$2a$10$qntpYmaH4ZPeChKtscpl8OyRSHYszkv180TiJ1Nn3J1FEUHNmOdOq', 0, 0);
INSERT INTO app_user_role (user_id, role_name) VALUES ((SELECT id FROM app_user WHERE username = 'admin'), 'ADMIN');
INSERT INTO app_user_role (user_id, role_name) VALUES ((SELECT id FROM app_user WHERE username = 'operatore'), 'OPERATOR');
INSERT INTO app_user_role (user_id, role_name) VALUES ((SELECT id FROM app_user WHERE username = 'sospeso'), 'OPERATOR');
INSERT INTO cliente (ragione_sociale, partita_iva, indirizzo, eliminato) VALUES ('Acme Costruzioni SRL', '01234567890', 'Via Roma 1, Milano', 0);
