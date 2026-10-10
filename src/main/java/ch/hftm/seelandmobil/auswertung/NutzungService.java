package ch.hftm.seelandmobil.auswertung;

import ch.hftm.seelandmobil.common.UngueltigeAnfrageException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/** A7 Nutzung auswerten für den Zeitraum [von, bis). */
@Service
public class NutzungService {

    private final NutzungRepository repository;

    public NutzungService(NutzungRepository repository) {
        this.repository = repository;
    }

    public List<StandortNutzung> jeStandort(OffsetDateTime von, OffsetDateTime bis) {
        pruefeZeitraum(von, bis);
        return repository.jeStandort(von, bis);
    }

    public List<FahrzeugNutzung> jeFahrzeug(OffsetDateTime von, OffsetDateTime bis, Long standortId) {
        pruefeZeitraum(von, bis);
        BigDecimal stunden = BigDecimal.valueOf(Duration.between(von, bis).toSeconds())
                .divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);
        return repository.jeFahrzeug(von, bis, stunden, standortId);
    }

    private static void pruefeZeitraum(OffsetDateTime von, OffsetDateTime bis) {
        if (!bis.isAfter(von)) {
            throw new UngueltigeAnfrageException("bis muss nach von liegen.");
        }
    }
}
