package ch.hftm.seelandmobil.fahrzeug;

/**
 * Der Bestand an Fahrzeugen in Betrieb hat sich an einem Standort geändert: Fahrzeug erfasst,
 * Standort gewechselt oder ausser Betrieb genommen. Wird innerhalb der Transaktion veröffentlicht.
 */
public record FahrzeugbestandGeaendert(Long fahrzeugId) {
}
