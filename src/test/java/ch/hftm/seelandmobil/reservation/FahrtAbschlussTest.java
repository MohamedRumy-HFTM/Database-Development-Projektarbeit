package ch.hftm.seelandmobil.reservation;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
import ch.hftm.seelandmobil.fahrzeug.Fahrzeug;
import ch.hftm.seelandmobil.fahrzeug.FahrzeugAenderung;
import ch.hftm.seelandmobil.fahrzeug.FahrzeugRepository;
import ch.hftm.seelandmobil.fahrzeug.FahrzeugService;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A5 Fahrt abschliessen und T4 Rollback. Ohne @Transactional: Commit und Rollback
 * sollen wirklich in PostgreSQL stattfinden. Die Testdaten werden danach gelöscht.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FahrtAbschlussTest {

    private static final int KM_STAND = 12_000;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FahrtService fahrtService;

    @Autowired
    private FahrzeugService fahrzeugService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StandortRepository standorte;

    @Autowired
    private FahrzeugRepository fahrzeuge;

    @Autowired
    private KundeRepository kunden;

    @Autowired
    private ReservationRepository reservationen;

    private final OffsetDateTime jetzt = OffsetDateTime.now();

    private Standort standort;
    private Fahrzeug fahrzeug;
    private Kunde kunde;
    private Reservation reservation;

    @BeforeEach
    void testdaten() {
        standort = standorte.findAll().stream()
                .filter(s -> s.getName().equals("Biel Bözingen"))
                .findFirst()
                .orElseThrow();
        fahrzeug = fahrzeuge.save(new Fahrzeug("BE 3001", "VW Golf", Kategorie.KOMBI, KM_STAND, standort));
        kunde = kunden.save(new Kunde("Mia Huber", "mia.huber@example.ch"));
        // Läuft seit drei Stunden; über die API ginge das nicht (Beginn in der Vergangenheit).
        reservation = reservationen.save(new Reservation(kunde, fahrzeug, jetzt.minusHours(3), jetzt.plusHours(1)));
    }

    @AfterEach
    void aufraeumen() {
        jdbc.update("delete from fahrt where reservation_id in (select id from reservation where fahrzeug_id = ?)",
                fahrzeug.getId());
        jdbc.update("delete from reservation where fahrzeug_id = ?", fahrzeug.getId());
        jdbc.update("delete from fahrzeug where id = ?", fahrzeug.getId());
        jdbc.update("delete from kunde where id = ?", kunde.getId());
    }

    @Test
    void abschlussFuehrtAlleDreiAenderungenDurch() throws Exception {
        abschliessen(12_250, jetzt.minusMinutes(10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kmStart").value(KM_STAND))
                .andExpect(jsonPath("$.kmEnde").value(12_250))
                .andExpect(jsonPath("$.gefahreneKm").value(250));

        assertThat(reservationStatus()).isEqualTo("ABGESCHLOSSEN");
        assertThat(anzahlFahrten()).isEqualTo(1);
        assertThat(kmStand()).isEqualTo(12_250);
    }

    /**
     * T4: Schritt 1 (bedingtes UPDATE der Reservation) ist bereits in der Datenbank ausgeführt,
     * wenn Schritt 2 am CHECK ck_fahrt_km scheitert. Hätte das UPDATE keine Zeile geändert,
     * käme 409 statt 400. Der Endzustand zeigt: PostgreSQL hat das UPDATE zurückgerollt.
     */
    @Test
    void kmEndeKleinerAlsKmStandRolltAuchDenStatusZurueck() throws Exception {
        abschliessen(11_500, jetzt.minusMinutes(10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Der km-Stand bei Rückgabe ist kleiner als der bisherige km-Stand des Fahrzeugs."));

        assertThat(reservationStatus()).isEqualTo("AKTIV");
        assertThat(anzahlFahrten()).isZero();
        assertThat(kmStand()).isEqualTo(KM_STAND);
        assertThat(version()).isZero();
    }

    /**
     * Zweiter Fehlerpfad: A5 hat das Fahrzeug gelesen, dann ändert A1 es und committet.
     * Das km-Update in Schritt 3 scheitert an der version; Schritt 1 und 2 werden zurückgerollt.
     */
    @Test
    void gleichzeitigeFahrzeugAenderungRolltAllesZurueck() {
        TransactionTemplate a5 = new TransactionTemplate(transactionManager);
        TransactionTemplate a1 = new TransactionTemplate(transactionManager);
        a1.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> a5.executeWithoutResult(status -> {
            fahrzeuge.findById(fahrzeug.getId()).orElseThrow();
            a1.executeWithoutResult(s -> fahrzeugService.aendern(fahrzeug.getId(),
                    new FahrzeugAenderung("BE 3001", "VW Golf Variant", Kategorie.KOMBI, standort.getId(), 0L)));
            fahrtService.abschliessen(reservation.getId(), new FahrtAbschluss(12_300, jetzt.minusMinutes(10)));
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(reservationStatus()).isEqualTo("AKTIV");
        assertThat(anzahlFahrten()).isZero();
        assertThat(kmStand()).isEqualTo(KM_STAND);
        assertThat(version()).as("nur die Änderung aus A1").isEqualTo(1);
    }

    @Test
    void zweiterAbschlussGibt409() throws Exception {
        abschliessen(12_250, jetzt.minusMinutes(10)).andExpect(status().isOk());

        abschliessen(12_400, jetzt.minusMinutes(5))
                .andExpect(status().isConflict());
        assertThat(anzahlFahrten()).isEqualTo(1);
        assertThat(kmStand()).isEqualTo(12_250);
    }

    @Test
    void stornierteReservationGibt409() throws Exception {
        reservation.stornieren();
        reservationen.save(reservation);

        abschliessen(12_250, jetzt.minusMinutes(10))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Die Reservation ist nicht mehr aktiv (bereits abgeschlossen oder storniert)."));
    }

    @Test
    void nochNichtBegonneneReservationGibt409() throws Exception {
        Reservation spaeter = reservationen.save(
                new Reservation(kunde, fahrzeug, jetzt.plusDays(2), jetzt.plusDays(2).plusHours(4)));

        mvc.perform(post("/api/reservationen/{id}/abschliessen", spaeter.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(12_250, jetzt)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Reservation hat noch nicht begonnen."));
    }

    @Test
    void rueckgabeVorBeginnGibt400() throws Exception {
        abschliessen(12_250, jetzt.minusHours(4))
                .andExpect(status().isBadRequest());
        assertThat(reservationStatus()).isEqualTo("AKTIV");
    }

    @Test
    void rueckgabeInDerZukunftGibt400() throws Exception {
        abschliessen(12_250, jetzt.plusHours(1))
                .andExpect(status().isBadRequest());
    }

    private ResultActions abschliessen(int kmEnde, OffsetDateTime rueckgabeAm) throws Exception {
        return mvc.perform(post("/api/reservationen/{id}/abschliessen", reservation.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(kmEnde, rueckgabeAm)));
    }

    private static String json(int kmEnde, OffsetDateTime rueckgabeAm) {
        return """
                {"kmEnde": %d, "rueckgabeAm": "%s"}
                """.formatted(kmEnde, rueckgabeAm);
    }

    private String reservationStatus() {
        return jdbc.queryForObject("select status from reservation where id = ?", String.class, reservation.getId());
    }

    private int anzahlFahrten() {
        return jdbc.queryForObject("select count(*) from fahrt where reservation_id in "
                + "(select id from reservation where fahrzeug_id = ?)", Integer.class, fahrzeug.getId());
    }

    private int kmStand() {
        return jdbc.queryForObject("select km_stand from fahrzeug where id = ?", Integer.class, fahrzeug.getId());
    }

    private long version() {
        return jdbc.queryForObject("select version from fahrzeug where id = ?", Long.class, fahrzeug.getId());
    }
}
