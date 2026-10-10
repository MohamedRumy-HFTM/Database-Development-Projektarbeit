package ch.hftm.seelandmobil.reservation;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/reservationen")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> reservieren(@Valid @RequestBody ReservationRequest request) {
        ReservationResponse reservation = service.reservieren(request);
        return ResponseEntity.created(URI.create("/api/reservationen/" + reservation.id())).body(reservation);
    }

    @GetMapping("/{id}")
    public ReservationResponse lesen(@PathVariable Long id) {
        return service.lesen(id);
    }
}
