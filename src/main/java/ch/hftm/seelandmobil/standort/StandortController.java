package ch.hftm.seelandmobil.standort;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Standorte sind Stammdaten aus der Migration und werden nur gelesen.
 */
@RestController
@RequestMapping("/api/standorte")
public class StandortController {

    private final StandortRepository standorte;

    public StandortController(StandortRepository standorte) {
        this.standorte = standorte;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<StandortResponse> alle() {
        return standorte.findAll(Sort.by("name")).stream()
                .map(StandortResponse::von)
                .toList();
    }
}
