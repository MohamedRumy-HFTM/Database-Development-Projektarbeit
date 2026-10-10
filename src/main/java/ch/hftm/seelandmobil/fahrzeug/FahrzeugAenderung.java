package ch.hftm.seelandmobil.fahrzeug;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Änderung der Stammdaten. Der km-Stand wird nur beim Fahrtabschluss nachgeführt.
 * version ist der zuletzt gelesene Stand; ist er veraltet, wird die Änderung abgelehnt.
 */
public record FahrzeugAenderung(
        @NotBlank
        @Pattern(regexp = "^[A-Z]{2} [0-9]{1,6}$", message = "muss das Format 'BE 12345' haben")
        String kennzeichen,

        @NotBlank
        @Size(max = 60)
        String modell,

        @NotNull
        Kategorie kategorie,

        @NotNull
        Long standortId,

        @NotNull
        Long version) {
}
