export class LoginResponseModel {
  username!: string;

  /** Ruoli dell'utente, letti dal token lato server: servono a nascondere quello che non può fare. */
  ruoli: string[] = [];
}
