package ch.hftm.seelandmobil.kunde;

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

    @GetMapping("/{id}")
    public KundeResponse lesen(@PathVariable Long id) {
        return service.lesen(id);
    }
}
