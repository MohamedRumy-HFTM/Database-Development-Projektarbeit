package ch.hftm.seelandmobil.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Übergang vom früheren Schema (bis V6) zum erweiterten Schema mit Abholstandort (V7),
 * wenn bereits Reservationen vorhanden sind.
 */
@Testcontainers
class AbholstandortMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Test
    void bestehendeReservationenErhaltenDenStandortIhresFahrzeugs() {
        DataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // früheres Schema mit Daten
        Flyway.configure().dataSource(dataSource).target("6").load().migrate();
        Long lyss = jdbc.queryForObject("SELECT id FROM standort WHERE name = 'Lyss Zentrum'", Long.class);
        Long fahrzeugId = jdbc.queryForObject("""
                INSERT INTO fahrzeug (kennzeichen, modell, kategorie, standort_id)
                VALUES ('BE 12345', 'VW Polo', 'KLEIN', ?)
                RETURNING id
                """, Long.class, lyss);
        Long kundeId = jdbc.queryForObject("""
                INSERT INTO kunde (name, email)
                VALUES ('Anna Muster', 'anna@test.ch')
                RETURNING id
                """, Long.class);
        jdbc.update("""
                INSERT INTO reservation (kunde_id, fahrzeug_id, beginn, ende, status)
                VALUES (?, ?, '2026-09-01 10:00+02', '2026-09-01 14:00+02', 'ABGESCHLOSSEN')
                """, kundeId, fahrzeugId);

        // erweitertes Schema
        Flyway.configure().dataSource(dataSource).load().migrate();

        Long abholstandort = jdbc.queryForObject("SELECT standort_id FROM reservation", Long.class);
        assertThat(abholstandort).isEqualTo(lyss);
        assertThatThrownBy(() -> jdbc.update("UPDATE reservation SET standort_id = NULL"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
