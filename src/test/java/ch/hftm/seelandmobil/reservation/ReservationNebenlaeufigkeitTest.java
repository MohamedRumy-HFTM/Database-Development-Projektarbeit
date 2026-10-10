package ch.hftm.seelandmobil.reservation;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
import ch.hftm.seelandmobil.fahrzeug.Fahrzeug;
import ch.hftm.seelandmobil.fahrzeug.FahrzeugRepository;
import ch.hftm.seelandmobil.fahrzeug.Kategorie;
import ch.hftm.seelandmobil.kunde.Kunde;
import ch.hftm.seelandmobil.kunde.KundeRepository;
import ch.hftm.seelandmobil.standort.Standort;
import ch.hftm.seelandmobil.standort.StandortRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T5 Schutz vor Konkurrenz. Transaktion A schreibt und wird vor dem COMMIT angehalten.
 * Transaktion B läuft gleichzeitig über die REST-API und muss nachweislich auf A warten
 * (pg_stat_activity). Erst nach dem COMMIT von A entscheidet die Datenbank über B.
 * <p>
 * Ohne @Transactional: Beide Transaktionen müssen wirklich committen. Die Testdaten
 * werden danach gelöscht.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReservationNebenlaeufigkeitTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private TransactionTemplate transaktion;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StandortRepository standorte;

    @Autowired
    private FahrzeugRepository fahrzeuge;

    @Autowired
    private KundeRepository kunden;

    private final ExecutorService threads = Executors.newFixedThreadPool(2);
    private final CountDownLatch aHatGeschrieben = new CountDownLatch(1);
    private final CountDownLatch aDarfCommitten = new CountDownLatch(1);

    private Fahrzeug fahrzeug;
    private Kunde kundeA;
    private Kunde kundeB;

    @BeforeEach
    void testdaten() {
        Standort nidau = standorte.findAll().stream()
                .filter(s -> s.getName().equals("Nidau"))
                .findFirst()
                .orElseThrow();
        fahrzeug = fahrzeuge.save(new Fahrzeug("BE 9001", "Renault Clio", Kategorie.KLEIN, 5000, nidau));
        kundeA = kunden.save(new Kunde("Kundin A", "kundin.a@example.ch"));
        kundeB = kunden.save(new Kunde("Kunde B", "kunde.b@example.ch"));
    }

    @AfterEach
    void aufraeumen() throws InterruptedException {
        aDarfCommitten.countDown();
        threads.shutdown();
        threads.awaitTermination(10, SECONDS);
        jdbc.update("delete from reservation where fahrzeug_id = ?", fahrzeug.getId());
        jdbc.update("delete from fahrzeug where id = ?", fahrzeug.getId());
        jdbc.update("delete from kunde where id in (?, ?)", kundeA.getId(), kundeB.getId());
    }

    @Test
    void gleichzeitigeUeberschneidendeReservationWartetUndScheitert() throws Exception {
        Future<ReservationResponse> a = reservierenUndVorCommitAnhalten(um(10), um(14));

        Future<MvcResult> b = threads.submit(() -> mvc.perform(post("/api/reservationen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(kundeB, um(12), um(16)))).andReturn());

        warteBisEineTransaktionBlockiertIst();
        assertThat(b.isDone()).as("B darf vor dem COMMIT von A nicht fertig sein").isFalse();

        aDarfCommitten.countDown();
        assertThat(a.get(10, SECONDS).status()).isEqualTo(ReservationStatus.AKTIV);
        MvcResult antwortB = b.get(10, SECONDS);
        assertThat(antwortB.getResponse().getStatus()).isEqualTo(409);
        assertThat(antwortB.getResponse().getContentAsString())
                .contains("Das Fahrzeug ist im gewünschten Zeitraum bereits reserviert.");
        assertThat(anzahlReservationen()).isEqualTo(1);
    }

    @Test
    void ausserbetriebnahmeWartetAufLaufendeReservation() throws Exception {
        Future<ReservationResponse> a = reservierenUndVorCommitAnhalten(um(10), um(14));

        Future<MvcResult> b = threads.submit(() -> mvc.perform(
                post("/api/fahrzeuge/{id}/ausser-betrieb", fahrzeug.getId())).andReturn());

        warteBisEineTransaktionBlockiertIst();
        assertThat(b.isDone()).as("B darf vor dem COMMIT von A nicht fertig sein").isFalse();

        aDarfCommitten.countDown();
        a.get(10, SECONDS);
        assertThat(b.get(10, SECONDS).getResponse().getStatus()).isEqualTo(409);
        assertThat(fahrzeuge.findById(fahrzeug.getId()).orElseThrow().isInBetrieb()).isTrue();
    }

    /** Transaktion A: Reservation schreiben, dann bis zur Freigabe vor dem COMMIT warten. */
    private Future<ReservationResponse> reservierenUndVorCommitAnhalten(OffsetDateTime beginn, OffsetDateTime ende)
            throws InterruptedException {
        Future<ReservationResponse> a = threads.submit(() -> transaktion.execute(status -> {
            ReservationResponse reservation = reservationService.reservieren(
                    new ReservationRequest(kundeA.getId(), fahrzeug.getId(), beginn, ende));
            aHatGeschrieben.countDown();
            warten(aDarfCommitten);
            return reservation;
        }));
        assertThat(aHatGeschrieben.await(10, SECONDS)).as("A hat nicht geschrieben").isTrue();
        return a;
    }

    private void warteBisEineTransaktionBlockiertIst() throws InterruptedException {
        long frist = System.nanoTime() + SECONDS.toNanos(10);
        while (System.nanoTime() < frist) {
            Integer wartend = jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where datname = current_database() and wait_event_type = 'Lock'
                    """, Integer.class);
            if (wartend != null && wartend == 1) {
                return;
            }
            Thread.sleep(20);
        }
        fail("Transaktion B wartet nicht auf Transaktion A.");
    }

    private int anzahlReservationen() {
        Integer anzahl = jdbc.queryForObject(
                "select count(*) from reservation where fahrzeug_id = ?", Integer.class, fahrzeug.getId());
        return anzahl == null ? 0 : anzahl;
    }

    private static void warten(CountDownLatch latch) {
        try {
            if (!latch.await(10, SECONDS)) {
                throw new IllegalStateException("Keine Freigabe für den COMMIT erhalten.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static OffsetDateTime um(int stunde) {
        return OffsetDateTime.of(2040, 3, 5, stunde, 0, 0, 0, ZoneOffset.ofHours(1));
    }

    private String json(Kunde kunde, OffsetDateTime beginn, OffsetDateTime ende) {
        return """
                {"kundeId": %d, "fahrzeugId": %d, "beginn": "%s", "ende": "%s"}
                """.formatted(kunde.getId(), fahrzeug.getId(), beginn, ende);
    }
}
