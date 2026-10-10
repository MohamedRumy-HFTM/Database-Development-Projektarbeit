package ch.hftm.seelandmobil.fahrzeug;

/** Ergebniszeile der Verfügbarkeitssuche (A6), direkt per JPQL-Konstruktorausdruck erzeugt. */
public record VerfuegbaresFahrzeug(
        Long id,
        String kennzeichen,
        String modell,
        Kategorie kategorie,
        Long standortId,
        String standortName) {
}
