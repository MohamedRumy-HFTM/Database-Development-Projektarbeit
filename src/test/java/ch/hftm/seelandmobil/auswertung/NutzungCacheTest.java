package ch.hftm.seelandmobil.auswertung;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
import ch.hftm.seelandmobil.reservation.FahrtAbschluss;
import ch.hftm.seelandmobil.reservation.FahrtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * T12 Cache der Standortauswertung (A7). Der Spy auf das JDBC-Repository zählt,
 * wie oft die SQL-Abfrage tatsächlich ausgeführt wird.
 * Ohne @Transactional: Der Cache wird erst nach einem echten COMMIT geleert.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NutzungCacheTest {

    @MockitoSpyBean
    private NutzungRepository repository;

    @Autowired
    private NutzungService service;

    @Autowired
    private FahrtService fahrtService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final OffsetDateTime jetzt = OffsetDateTime.now();
    private final OffsetDateTime von = jetzt.minusDays(2);
    private final OffsetDateTime bis = jetzt.plusDays(1);

    private long kunde;
    private long fahrzeug;
    private long laufendeReservation;

    @BeforeEach
    void testdaten() {
        long bueren = jdbc.queryForObject("select id from standort where name = 'Büren a.A.'", Long.class);
        kunde = jdbc.queryForObject(
                "insert into kunde (name, email) values ('Tim Roth', 'tim.roth@example.ch') returning id", Long.class);
        fahrzeug = jdbc.queryForObject("""
                insert into fahrzeug (kennzeichen, modell, kategorie, km_stand, standort_id)
                values ('BE 9201', 'Dacia Sandero', 'KLEIN', 5000, ?) returning id
                """, Long.class, bueren);
        long abgeschlossen = reservation(bueren, jetzt.minusHours(30), jetzt.minusHours(26), "ABGESCHLOSSEN");
        jdbc.update("insert into fahrt (reservation_id, km_start, km_ende, rueckgabe_am) values (?, 4900, 5000, ?)",
                abgeschlossen, jetzt.minusHours(27));
        laufendeReservation = reservation(bueren, jetzt.minusHours(3), jetzt.plusHours(1), "AKTIV");

        cacheManager.getCache(NutzungService.STANDORT_CACHE).clear();
        Mockito.clearInvocations(repository);
    }

    @AfterEach
    void aufraeumen() {
        jdbc.update("delete from fahrt where reservation_id in (select id from reservation where fahrzeug_id = ?)",
                fahrzeug);
        jdbc.update("delete from reservation where fahrzeug_id = ?", fahrzeug);
        jdbc.update("delete from fahrzeug where id = ?", fahrzeug);
        jdbc.update("delete from kunde where id = ?", kunde);
        cacheManager.getCache(NutzungService.STANDORT_CACHE).clear();
    }

    @Test
    void zweiterAufrufKommtOhneSqlAusDemCache() {
        assertThat(fahrtenBueren()).isEqualTo(1);
        assertThat(fahrtenBueren()).isEqualTo(1);

        verify(repository, times(1)).jeStandort(any(), any());
    }

    @Test
    void nachFahrtabschlussSofortDerNeueWert() {
        assertThat(fahrtenBueren()).isEqualTo(1);

        fahrtService.abschliessen(laufendeReservation, new FahrtAbschluss(5120, jetzt.minusMinutes(5)));

        assertThat(fahrtenBueren()).isEqualTo(2);
        verify(repository, times(2)).jeStandort(any(), any());
    }

    /**
     * Warum erst nach dem COMMIT geleert wird: Eine gleichzeitige Abfrage zwischen Fahrtabschluss
     * und COMMIT sieht noch den alten Stand und legt ihn in den Cache. Würde schon beim
     * Fahrtabschluss geleert, bliebe dieser veraltete Eintrag danach stehen.
     */
    @Test
    void abfrageVorDemCommitHinterlaesstKeinenVeraltetenEintrag() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            fahrtService.abschliessen(laufendeReservation, new FahrtAbschluss(5120, jetzt.minusMinutes(5)));
            long vorDemCommit = CompletableFuture.supplyAsync(this::fahrtenBueren).join();
            assertThat(vorDemCommit).as("andere Transaktion sieht den alten Stand").isEqualTo(1);
        });

        assertThat(fahrtenBueren()).isEqualTo(2);
    }

    @Test
    void zurueckgerollterAbschlussLaesstDenCacheGueltig() {
        assertThat(fahrtenBueren()).isEqualTo(1);

        assertThatThrownBy(() -> fahrtService.abschliessen(laufendeReservation,
                new FahrtAbschluss(4000, jetzt.minusMinutes(5))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(fahrtenBueren()).isEqualTo(1);
        verify(repository, times(1)).jeStandort(any(), any());
    }

    @Test
    void gleicherZeitraumInAndererZeitzoneTrifftDenselbenEintrag() {
        service.jeStandort(von, bis);
        service.jeStandort(von.withOffsetSameInstant(ZoneOffset.UTC), bis.withOffsetSameInstant(ZoneOffset.UTC));

        verify(repository, times(1)).jeStandort(any(), any());
    }

    private long fahrtenBueren() {
        return service.jeStandort(von, bis).stream()
                .filter(nutzung -> nutzung.standortName().equals("Büren a.A."))
                .findFirst()
                .orElseThrow()
                .anzahlFahrten();
    }

    private long reservation(long standortId, OffsetDateTime beginn, OffsetDateTime ende, String status) {
        return jdbc.queryForObject("""
                insert into reservation (kunde_id, fahrzeug_id, standort_id, beginn, ende, status)
                values (?, ?, ?, ?, ?, ?) returning id
                """, Long.class, kunde, fahrzeug, standortId, beginn, ende, status);
    }
}
