package ch.hftm.seelandmobil.auswertung;

import ch.hftm.seelandmobil.fahrzeug.FahrzeugbestandGeaendert;
import ch.hftm.seelandmobil.reservation.FahrtAbgeschlossen;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Die Standortauswertung hängt von zwei Dingen ab: von den abgeschlossenen Fahrten mit ihrem
 * Abholstandort (ändert nur A5) und vom Fahrzeugbestand je Standort, dem Nenner der Auslastung
 * (ändern Erfassen, Standortwechsel und Ausserbetriebnahme in A1). Standorte sind fix,
 * Abholstandorte ändern sich nach der Buchung nicht. Nur nach diesen Änderungen wird der Cache
 * geleert – erst nach dem COMMIT: Vorher könnte eine gleichzeitige Abfrage noch den alten Stand
 * lesen und erneut in den Cache legen. Bei einem Rollback bleibt der Cache gültig.
 */
@Component
class StandortNutzungCacheLeeren {

    private final CacheManager cacheManager;

    StandortNutzungCacheLeeren(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void fahrtAbgeschlossen(FahrtAbgeschlossen ereignis) {
        leeren();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void fahrzeugbestandGeaendert(FahrzeugbestandGeaendert ereignis) {
        leeren();
    }

    private void leeren() {
        Cache cache = cacheManager.getCache(NutzungService.STANDORT_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }
}
