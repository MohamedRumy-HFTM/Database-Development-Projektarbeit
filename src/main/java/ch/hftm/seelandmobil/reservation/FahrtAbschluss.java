package ch.hftm.seelandmobil.reservation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.OffsetDateTime;

/**
 * Rückgabe eines Fahrzeugs. Ob kmEnde mindestens dem bisherigen km-Stand entspricht,
 * prüft bewusst die Datenbank (ck_fahrt_km), nicht Java.
 */
public record FahrtAbschluss(
        @NotNull
        @PositiveOrZero
        Integer kmEnde,

        @NotNull
        OffsetDateTime rueckgabeAm) {
}
