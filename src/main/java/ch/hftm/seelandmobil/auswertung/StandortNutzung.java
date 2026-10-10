package ch.hftm.seelandmobil.auswertung;

import java.math.BigDecimal;

/** A7: Nutzung eines Standorts im Zeitraum, zugeordnet nach Abholstandort der Reservation. */
public record StandortNutzung(
        Long standortId,
        String standortName,
        long anzahlFahrten,
        BigDecimal nutzungsstunden,
        long km) {
}
