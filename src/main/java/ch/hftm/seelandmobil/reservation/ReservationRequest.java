package ch.hftm.seelandmobil.reservation;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** Zeitraum halboffen [beginn, ende). */
public record ReservationRequest(
        @NotNull
        Long kundeId,

        @NotNull
        Long fahrzeugId,

        @NotNull
        OffsetDateTime beginn,

        @NotNull
        OffsetDateTime ende) {
}
