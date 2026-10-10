package ch.hftm.seelandmobil.reservation;

import java.time.OffsetDateTime;

public record ReservationResponse(
        Long id,
        Long kundeId,
        Long fahrzeugId,
        String kennzeichen,
        Long standortId,
        String standortName,
        OffsetDateTime beginn,
        OffsetDateTime ende,
        ReservationStatus status) {

    static ReservationResponse von(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getKunde().getId(),
                reservation.getFahrzeug().getId(),
                reservation.getFahrzeug().getKennzeichen(),
                reservation.getStandort().getId(),
                reservation.getStandort().getName(),
                reservation.getBeginn(),
                reservation.getEnde(),
                reservation.getStatus());
    }
}
