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
