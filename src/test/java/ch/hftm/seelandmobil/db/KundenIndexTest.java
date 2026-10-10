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

/**
 * V9: Der Index passt zur A4-Abfrage. Mit wenigen Testzeilen würde PostgreSQL ohnehin die Tabelle
 * lesen; deshalb werden Seq Scan und Bitmap Scan hier abgeschaltet, um zu prüfen, ob der Index
 * die Sortierung vollständig liefert (kein Sort-Knoten im Plan). Die Messung mit 121'880 Reservationen steht
 * in docs/performance.md.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class KundenIndexTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void a4AbfrageLiestSortiertAusDemIndex() {
        jdbc.execute("SET LOCAL enable_seqscan = off");
        jdbc.execute("SET LOCAL enable_bitmapscan = off");

        List<String> plan = jdbc.queryForList("""
                EXPLAIN (COSTS OFF)
                SELECT r.id FROM reservation r
                WHERE r.kunde_id = 1
                ORDER BY r.beginn DESC, r.id DESC
                FETCH FIRST 20 ROWS ONLY
                """, String.class);

        assertThat(String.join("\n", plan))
                .contains("using ix_reservation_kunde_beginn")
                .doesNotContain("Sort");
    }
}
