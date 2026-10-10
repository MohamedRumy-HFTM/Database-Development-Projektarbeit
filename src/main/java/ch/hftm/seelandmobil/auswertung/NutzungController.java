package ch.hftm.seelandmobil.auswertung;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/** A7 Nutzung auswerten. */
@RestController
@RequestMapping("/api/auswertungen")
public class NutzungController {

    private final NutzungService service;

    public NutzungController(NutzungService service) {
        this.service = service;
    }

    @GetMapping("/standorte")
    public List<StandortNutzung> jeStandort(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime von,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime bis) {
        return service.jeStandort(von, bis);
    }

    @GetMapping("/fahrzeuge")
    public List<FahrzeugNutzung> jeFahrzeug(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime von,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime bis,
            @RequestParam(required = false) Long standortId) {
        return service.jeFahrzeug(von, bis, standortId);
    }
}
