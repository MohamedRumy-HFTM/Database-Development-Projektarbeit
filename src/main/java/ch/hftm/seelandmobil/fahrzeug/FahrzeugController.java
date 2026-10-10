package ch.hftm.seelandmobil.fahrzeug;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

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
