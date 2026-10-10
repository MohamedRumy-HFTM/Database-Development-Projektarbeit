package ch.hftm.seelandmobil.db;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void leereDatenbankWirdVollstaendigAufgebaut() {
        Integer fehlgeschlagen = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        List<String> tabellen = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """, String.class);

        assertThat(fehlgeschlagen).isZero();
        assertThat(tabellen).containsExactly("fahrt", "fahrzeug", "kunde", "reservation", "standort");
    }

    @Test
    void standorteSindVorgegeben() {
        Integer anzahl = jdbc.queryForObject("SELECT count(*) FROM standort", Integer.class);

        assertThat(anzahl).isEqualTo(8);
    }
}
