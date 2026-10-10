package ch.hftm.seelandmobil.db;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prüft die zentralen Regeln direkt in der Datenbank, ohne Anwendungslogik.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SchemaConstraintsTest {

    private static final String UEBERSCHNEIDUNG = "23P01";
    private static final String CHECK_VERLETZT = "23514";
    private static final String UNIQUE_VERLETZT = "23505";

    @Autowired
    private JdbcTemplate jdbc;

    private Long fahrzeugId;
    private Long kundeId;

    @BeforeEach
    void testdatenAnlegen() {
        Long standortId = jdbc.queryForObject("SELECT id FROM standort WHERE name = 'Biel Bahnhof'", Long.class);
        fahrzeugId = jdbc.queryForObject("""
                INSERT INTO fahrzeug (kennzeichen, modell, kategorie, km_stand, standort_id)
                VALUES ('BE 12345', 'VW Polo', 'KLEIN', 12000, ?)
                RETURNING id
                """, Long.class, standortId);
        kundeId = jdbc.queryForObject("""
                INSERT INTO kunde (name, email)
                VALUES ('Anna Muster', 'anna@test.ch')
                RETURNING id
                """, Long.class);
    }

    @Test
    void ueberschneidendeReservationWirdAbgelehnt() {
        reservation("2027-01-10 10:00+01", "2027-01-10 14:00+01", "AKTIV");

        assertThatThrownBy(() -> reservation("2027-01-10 12:00+01", "2027-01-10 16:00+01", "AKTIV"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(sqlState(ex)).isEqualTo(UEBERSCHNEIDUNG));
    }

    @Test
    void direktAnschliessendeReservationIstErlaubt() {
        reservation("2027-01-10 10:00+01", "2027-01-10 14:00+01", "AKTIV");
        reservation("2027-01-10 14:00+01", "2027-01-10 16:00+01", "AKTIV");

        assertThat(anzahlReservationen()).isEqualTo(2);
    }

    @Test
    void stornierteReservationGibtZeitraumFrei() {
        reservation("2027-01-10 10:00+01", "2027-01-10 14:00+01", "STORNIERT");
        reservation("2027-01-10 12:00+01", "2027-01-10 16:00+01", "AKTIV");

        assertThat(anzahlReservationen()).isEqualTo(2);
    }

    @Test
    void endeVorBeginnWirdAbgelehnt() {
        assertThatThrownBy(() -> reservation("2027-01-10 14:00+01", "2027-01-10 10:00+01", "AKTIV"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(sqlState(ex)).isEqualTo(CHECK_VERLETZT));
    }

    @Test
    void unbekannterStatusWirdAbgelehnt() {
        assertThatThrownBy(() -> reservation("2027-01-10 10:00+01", "2027-01-10 14:00+01", "OFFEN"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(sqlState(ex)).isEqualTo(CHECK_VERLETZT));
    }

    @Test
    void kmEndeKleinerAlsKmStartWirdAbgelehnt() {
        Long reservationId = reservation("2027-01-10 10:00+01", "2027-01-10 14:00+01", "AKTIV");

        assertThatThrownBy(() -> fahrt(reservationId, 12000, 11500))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(sqlState(ex)).isEqualTo(CHECK_VERLETZT));
    }

    @Test
    void proReservationGibtEsHoechstensEineFahrt() {
        Long reservationId = reservation("2027-01-10 10:00+01", "2027-01-10 14:00+01", "ABGESCHLOSSEN");
        fahrt(reservationId, 12000, 12080);

        assertThatThrownBy(() -> fahrt(reservationId, 12080, 12100))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(sqlState(ex)).isEqualTo(UNIQUE_VERLETZT));
    }

    @Test
    void emailIstUnabhaengigVonGrossKleinschreibungEindeutig() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO kunde (name, email) VALUES ('Anna Muster', 'Anna@Test.ch')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(sqlState(ex)).isEqualTo(UNIQUE_VERLETZT));
    }

    @Test
    void kennzeichenIstEindeutig() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO fahrzeug (kennzeichen, modell, kategorie, standort_id)
                SELECT 'BE 12345', 'Skoda Octavia', 'KOMBI', standort_id FROM fahrzeug WHERE id = ?
                """, fahrzeugId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(sqlState(ex)).isEqualTo(UNIQUE_VERLETZT));
    }

    private Long reservation(String beginn, String ende, String status) {
        return jdbc.queryForObject("""
                INSERT INTO reservation (kunde_id, fahrzeug_id, beginn, ende, status)
                VALUES (?, ?, ?::timestamptz, ?::timestamptz, ?)
                RETURNING id
                """, Long.class, kundeId, fahrzeugId, beginn, ende, status);
    }

    private void fahrt(Long reservationId, int kmStart, int kmEnde) {
        jdbc.update("""
                INSERT INTO fahrt (reservation_id, km_start, km_ende, rueckgabe_am)
                VALUES (?, ?, ?, now())
                """, reservationId, kmStart, kmEnde);
    }

    private Integer anzahlReservationen() {
        return jdbc.queryForObject("SELECT count(*) FROM reservation WHERE fahrzeug_id = ?", Integer.class, fahrzeugId);
    }

    private static String sqlState(Throwable ex) {
        Throwable ursache = NestedExceptionUtils.getMostSpecificCause(ex);
        return ursache instanceof SQLException sql ? sql.getSQLState() : null;
    }
}
