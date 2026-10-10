package ch.hftm.seelandmobil.auswertung;

import ch.hftm.seelandmobil.reservation.FahrtAbgeschlossen;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Die Standortauswertung hängt nur von abgeschlossenen Fahrten und deren Abholstandort ab.
 * Standorte sind fix, Abholstandorte ändern sich nach der Buchung nicht. Also ändert nur A5
 * das Ergebnis, und nur dann wird der Cache geleert – erst nach dem COMMIT: Vorher könnte
 * eine gleichzeitige Abfrage noch den alten Stand lesen und erneut in den Cache legen.
 * Bei einem Rollback bleibt der Cache gültig.
 */
@Component
class StandortNutzungCacheLeeren {

    private final CacheManager cacheManager;

    StandortNutzungCacheLeeren(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void fahrtAbgeschlossen(FahrtAbgeschlossen ereignis) {
        Cache cache = cacheManager.getCache(NutzungService.STANDORT_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }
}
