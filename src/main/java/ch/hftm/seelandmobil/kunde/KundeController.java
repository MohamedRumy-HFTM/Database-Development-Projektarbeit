package ch.hftm.seelandmobil.kunde;

import ch.hftm.seelandmobil.common.Seite;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
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

@RestController
@RequestMapping("/api/kunden")
public class KundeController {

    private final KundeService service;

    public KundeController(KundeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<KundeResponse> erfassen(@Valid @RequestBody KundeRequest request) {
        KundeResponse kunde = service.erfassen(request);
        return ResponseEntity.created(URI.create("/api/kunden/" + kunde.id())).body(kunde);
    }

    @GetMapping
    public Seite<KundeResponse> suchen(
            @RequestParam(required = false) String suche,
            @RequestParam(defaultValue = "0") @PositiveOrZero int seite,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int groesse) {
        return service.suchen(suche, seite, groesse);
    }

    @GetMapping("/{id}")
    public KundeResponse lesen(@PathVariable Long id) {
        return service.lesen(id);
    }

    @PutMapping("/{id}")
    public KundeResponse aendern(@PathVariable Long id, @Valid @RequestBody KundeRequest request) {
        return service.aendern(id, request);
    }

    @PostMapping("/{id}/sperren")
    public KundeResponse sperren(@PathVariable Long id) {
        return service.sperren(id);
    }
}
