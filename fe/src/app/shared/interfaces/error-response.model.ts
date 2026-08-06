/**
 * Formato unico degli errori dell'API (`ErrorResponse`): il backend risponde
 * sempre così, quindi qui basta un solo tipo per leggerli tutti.
 */
export interface ErrorResponseModel {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path?: string;
  /** Solo sui 400 di validazione: per ogni campo, cosa non va. */
  fieldErrors?: Record<string, string[]>;
  /** Solo sui 500: il codice con cui ritrovare l'errore nei log. */
  traceId?: string;
}
