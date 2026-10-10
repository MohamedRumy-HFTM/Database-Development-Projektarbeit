package ch.hftm.seelandmobil.auswertung;

import java.math.BigDecimal;

/** A7: Nutzung eines Fahrzeugs im Zeitraum. Auslastung = Nutzungsstunden ÷ Stunden des Zeitraums. */
public record FahrzeugNutzung(
        Long fahrzeugId,
        String kennzeichen,
        String standortName,
        long anzahlFahrten,
        BigDecimal nutzungsstunden,
        long km,
        BigDecimal auslastungProzent) {
}
