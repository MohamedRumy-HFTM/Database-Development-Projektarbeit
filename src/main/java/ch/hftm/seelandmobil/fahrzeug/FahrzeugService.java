package ch.hftm.seelandmobil.fahrzeug;

import ch.hftm.seelandmobil.common.NotFoundException;
import ch.hftm.seelandmobil.standort.Standort;
import ch.hftm.seelandmobil.standort.StandortRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FahrzeugService {

    private final FahrzeugRepository fahrzeuge;
    private final StandortRepository standorte;

    public FahrzeugService(FahrzeugRepository fahrzeuge, StandortRepository standorte) {
        this.fahrzeuge = fahrzeuge;
        this.standorte = standorte;
    }

    public FahrzeugResponse erfassen(FahrzeugRequest request) {
        Standort standort = standortLaden(request.standortId());
        Fahrzeug fahrzeug = new Fahrzeug(request.kennzeichen(), request.modell(), request.kategorie(),
                request.kmStand(), standort);
        return FahrzeugResponse.von(fahrzeuge.saveAndFlush(fahrzeug));
    }

    @Transactional(readOnly = true)
    public FahrzeugResponse lesen(Long id) {
        return FahrzeugResponse.von(laden(id));
    }

    private Fahrzeug laden(Long id) {
        return fahrzeuge.findById(id)
                .orElseThrow(() -> new NotFoundException("Fahrzeug " + id + " existiert nicht."));
    }

    private Standort standortLaden(Long id) {
        return standorte.findById(id)
                .orElseThrow(() -> new NotFoundException("Standort " + id + " existiert nicht."));
    }
}
