package ch.hftm.seelandmobil.fahrzeug;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record FahrzeugRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z]{2} [0-9]{1,6}$", message = "muss das Format 'BE 12345' haben")
        String kennzeichen,

        @NotBlank
        @Size(max = 60)
        String modell,

        @NotNull
        Kategorie kategorie,

        @NotNull
        @PositiveOrZero
        Integer kmStand,

        @NotNull
        Long standortId) {
}
