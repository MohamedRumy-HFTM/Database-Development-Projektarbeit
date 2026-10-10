package ch.hftm.seelandmobil.auswertung;

import ch.hftm.seelandmobil.common.UngueltigeAnfrageException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/** A7 Nutzung auswerten für den Zeitraum [von, bis). */
@Service
public class NutzungService {

    static final String STANDORT_CACHE = "standortNutzung";

    private final NutzungRepository repository;

    public NutzungService(NutzungRepository repository) {
        this.repository = repository;
    }

    /**
     * Gecacht pro Zeitraum. Schlüssel sind die Zeitpunkte als Instant: 10:00+02:00 und 08:00Z
     * sind derselbe Zeitraum. Geleert wird nach jedem Fahrtabschluss, siehe StandortNutzungCacheLeeren.
     */
    @Cacheable(cacheNames = STANDORT_CACHE, key = "{#von.toInstant(), #bis.toInstant()}")
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
