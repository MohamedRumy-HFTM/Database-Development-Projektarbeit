package ch.hftm.seelandmobil.db;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class FahrtNutzungViewTest {

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
    void nurAbgeschlosseneFahrtenWerdenGezaehlt() {
        Long abgeschlossen = reservation("2026-09-01 10:00+02", "2026-09-01 14:00+02", "ABGESCHLOSSEN");
        reservation("2026-09-02 10:00+02", "2026-09-02 14:00+02", "STORNIERT");
        reservation("2027-01-10 10:00+01", "2027-01-10 14:00+01", "AKTIV");
        jdbc.update("""
                INSERT INTO fahrt (reservation_id, km_start, km_ende, rueckgabe_am)
                VALUES (?, 12000, 12080, '2026-09-01 13:30+02')
                """, abgeschlossen);

        Integer anzahl = jdbc.queryForObject(
                "SELECT count(*) FROM v_fahrt_nutzung WHERE fahrzeug_id = ?", Integer.class, fahrzeugId);
        BigDecimal stunden = jdbc.queryForObject(
                "SELECT nutzung_h FROM v_fahrt_nutzung WHERE reservation_id = ?", BigDecimal.class, abgeschlossen);
        Integer km = jdbc.queryForObject(
                "SELECT km FROM v_fahrt_nutzung WHERE reservation_id = ?", Integer.class, abgeschlossen);

        assertThat(anzahl).isEqualTo(1);
        assertThat(stunden).isEqualByComparingTo("3.5");
        assertThat(km).isEqualTo(80);
    }

    private Long reservation(String beginn, String ende, String status) {
        return jdbc.queryForObject("""
                INSERT INTO reservation (kunde_id, fahrzeug_id, standort_id, beginn, ende, status)
                SELECT ?, id, standort_id, ?::timestamptz, ?::timestamptz, ?
                FROM fahrzeug
                WHERE id = ?
                RETURNING id
                """, Long.class, kundeId, beginn, ende, status, fahrzeugId);
    }
}
