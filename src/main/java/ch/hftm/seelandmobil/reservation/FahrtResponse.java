package ch.hftm.seelandmobil.reservation;

import java.time.OffsetDateTime;

public record FahrtResponse(
        Long id,
        Long reservationId,
        Long fahrzeugId,
        int kmStart,
        int kmEnde,
        int gefahreneKm,
        OffsetDateTime rueckgabeAm) {

    static FahrtResponse von(Fahrt fahrt, Long fahrzeugId) {
        return new FahrtResponse(
                fahrt.getId(),
                fahrt.getReservation().getId(),
                fahrzeugId,
                fahrt.getKmStart(),
                fahrt.getKmEnde(),
                fahrt.getKmEnde() - fahrt.getKmStart(),
                fahrt.getRueckgabeAm());
    }
}
