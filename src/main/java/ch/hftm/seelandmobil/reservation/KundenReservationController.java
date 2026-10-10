package ch.hftm.seelandmobil.reservation;

import ch.hftm.seelandmobil.common.Seite;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A4 Reservationen eines Kunden einsehen. */
@RestController
public class KundenReservationController {

    private final ReservationService service;

    public KundenReservationController(ReservationService service) {
        this.service = service;
    }

    @GetMapping("/api/kunden/{kundeId}/reservationen")
    public Seite<ReservationResponse> vonKunde(
            @PathVariable Long kundeId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "0") @PositiveOrZero int seite,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int groesse) {
        return service.vonKunde(kundeId, status, seite, groesse);
    }
}
