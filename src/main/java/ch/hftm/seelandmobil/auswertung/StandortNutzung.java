package ch.hftm.seelandmobil.auswertung;

import java.math.BigDecimal;

/**
 * A7: Nutzung eines Standorts im Zeitraum, zugeordnet nach Abholstandort der Reservation.
 * Auslastung = Nutzungsstunden ÷ (Fahrzeuge in Betrieb am Standort × Stunden des Zeitraums);
 * null, wenn dem Standort heute kein Fahrzeug in Betrieb zugeordnet ist.
 */
public record StandortNutzung(
        Long standortId,
        String standortName,
        long anzahlFahrzeuge,
        long anzahlFahrten,
        BigDecimal nutzungsstunden,
        long km,
        BigDecimal auslastungProzent) {
}
