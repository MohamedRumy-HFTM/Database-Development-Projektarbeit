package ch.hftm.seelandmobil.auswertung;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/** A7 Nutzung auswerten. */
@RestController
@RequestMapping("/api/auswertungen")
public class NutzungController {

    private final NutzungService service;

    public NutzungController(NutzungService service) {
        this.service = service;
    }

    /**
     * Der Header {@code Server-Timing} enthält die Zeit für die Auswertung im Server. Ein Treffer im
     * Cache braucht unter 1 ms, eine Abfrage der Datenbank deutlich mehr. So zeigt die Oberfläche den Cache.
     */
    @GetMapping("/standorte")
    public ResponseEntity<List<StandortNutzung>> jeStandort(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime von,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime bis) {
        long start = System.nanoTime();
        List<StandortNutzung> ergebnis = service.jeStandort(von, bis);
        double millisekunden = (System.nanoTime() - start) / 1_000_000.0;
        return ResponseEntity.ok()
                .header("Server-Timing", String.format(Locale.ROOT, "auswertung;dur=%.2f", millisekunden))
                .body(ergebnis);
    }

    @GetMapping("/fahrzeuge")
    public List<FahrzeugNutzung> jeFahrzeug(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime von,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime bis,
            @RequestParam(required = false) Long standortId) {
        return service.jeFahrzeug(von, bis, standortId);
    }
}
