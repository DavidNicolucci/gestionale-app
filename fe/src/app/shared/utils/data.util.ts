/**
 * Conversioni fra le date di Angular Material e quelle del backend.
 *
 * Il datepicker lavora con `Date`, il backend con `LocalDate` serializzato come
 * `yyyy-MM-dd`. Sono due cose diverse: `Date` è un istante, `LocalDate` è un
 * giorno sul calendario. La traduzione sta qui e non nei model di sezione perché
 * la stessa data va e viene in tutte le anagrafiche.
 */

/**
 * `toISOString()` NON va bene: converte in UTC, e per chi sta a est di Greenwich
 * la mezzanotte locale del 15 diventa il 14 alle 23:00. Quindi si leggono anno,
 * mese e giorno come li vede l'utente e si compone la stringa a mano.
 */
export function aDataIso(data: Date | null | undefined): string | undefined {
  if (!data) {
    return undefined;
  }

  const anno = data.getFullYear();
  const mese = `${data.getMonth() + 1}`.padStart(2, '0');
  const giorno = `${data.getDate()}`.padStart(2, '0');

  return `${anno}-${mese}-${giorno}`;
}

/**
 * Il verso opposto: dal `yyyy-MM-dd` del backend alla `Date` del datepicker.
 * Serve alle pagine di modifica, che riempiono il form con quello che è già salvato.
 *
 * `new Date('2026-09-19')` NON va bene: quel formato lo standard lo interpreta come
 * UTC, quindi a ovest di Greenwich il datepicker mostrerebbe il giorno prima. Qui
 * spezziamo la stringa e costruiamo la data con anno, mese e giorno separati, che è
 * mezzanotte locale ovunque.
 */
export function daDataIso(data: string | null | undefined): Date | null {
  if (!data) {
    return null;
  }

  const [anno, mese, giorno] = data.split('-').map(Number);

  if (!anno || !mese || !giorno) {
    return null;
  }

  return new Date(anno, mese - 1, giorno);
}
