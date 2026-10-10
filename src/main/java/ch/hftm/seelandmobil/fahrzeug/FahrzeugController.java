package ch.hftm.seelandmobil.fahrzeug;

import ch.hftm.seelandmobil.common.Seite;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/fahrzeuge")
public class FahrzeugController {

    private final FahrzeugService service;

    public FahrzeugController(FahrzeugService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<FahrzeugResponse> erfassen(@Valid @RequestBody FahrzeugRequest request) {
        FahrzeugResponse fahrzeug = service.erfassen(request);
        return ResponseEntity.created(URI.create("/api/fahrzeuge/" + fahrzeug.id())).body(fahrzeug);
    }

    @GetMapping("/verfuegbar")
    public Seite<VerfuegbaresFahrzeug> verfuegbar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime von,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime bis,
            @RequestParam(required = false) Long standortId,
            @RequestParam(required = false) Kategorie kategorie,
            @RequestParam(defaultValue = "0") @PositiveOrZero int seite,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int groesse) {
        return service.verfuegbareSuchen(von, bis, standortId, kategorie, seite, groesse);
    }

    @GetMapping("/{id}")
    public FahrzeugResponse lesen(@PathVariable Long id) {
        return service.lesen(id);
    }

    @PutMapping("/{id}")
    public FahrzeugResponse aendern(@PathVariable Long id, @Valid @RequestBody FahrzeugAenderung aenderung) {
        return service.aendern(id, aenderung);
    }

    @PostMapping("/{id}/ausser-betrieb")
    public FahrzeugResponse ausserBetriebNehmen(@PathVariable Long id) {
        return service.ausserBetriebNehmen(id);
    }
}
