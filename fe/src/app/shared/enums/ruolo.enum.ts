/**
 * Ruoli applicativi, gli stessi nomi che stanno in `app_user_role` e nei
 * `@RolesAllowed` del backend. Servono solo a decidere cosa mostrare: il
 * permesso vero lo verifica il server sul token firmato.
 */
export enum Ruolo {
  ADMIN = 'ADMIN',
  OPERATOR = 'OPERATOR',
}
