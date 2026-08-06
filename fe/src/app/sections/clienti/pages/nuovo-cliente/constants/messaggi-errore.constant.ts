import { LUNGHEZZA_MAX_PARTITA_IVA } from '../../../constants/cliente.constants';

/**
 * Testi degli errori di validazione del form. Non stanno in nessuna delle due
 * classi perché le riguardano entrambe: il form service produce gli errori, il
 * componente li mostra.
 */
export const MESSAGGI_ERRORE = {
  RAGIONE_SOCIALE_OBBLIGATORIA: 'La ragione sociale è obbligatoria',
  PARTITA_IVA_TROPPO_LUNGA: `La partita IVA non può superare ${LUNGHEZZA_MAX_PARTITA_IVA} caratteri`,
} as const;
