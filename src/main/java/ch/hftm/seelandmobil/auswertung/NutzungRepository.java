package ch.hftm.seelandmobil.auswertung;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A7 per JDBC. Grundlage ist ausschliesslich die View v_fahrt_nutzung: Sie legt fest,
 * was als abgeschlossene Fahrt zählt. Zugeordnet wird nach Reservationsbeginn im
 * halboffenen Zeitraum [von, bis).
 * <p>
 * Der Zeitfilter steht in der ON-Bedingung des LEFT JOIN, nicht im WHERE – sonst fielen
 * Standorte und Fahrzeuge ohne Fahrt aus dem Ergebnis. Gezählt wird
 * COUNT(v.reservation_id), denn COUNT(*) ergäbe für die NULL-Zeile des LEFT JOIN 1 statt 0.
 * <p>
 * Den Fahrzeugbestand je Standort zählt ein eigener Teil (WITH bestand) vorab. Ein direkter
 * JOIN auf fahrzeug würde jede Fahrt einmal pro Fahrzeug des Standorts wiederholen und Fahrten,
 * Stunden und Kilometer vervielfachen.
 */
@Repository
public class NutzungRepository {

    private static final String JE_STANDORT = """
            WITH bestand AS (
                SELECT standort_id, COUNT(*) AS anzahl_fahrzeuge
                FROM fahrzeug
                WHERE in_betrieb
                GROUP BY standort_id
            )
            SELECT s.id                                  AS standort_id,
                   s.name                                AS standort_name,
                   COALESCE(b.anzahl_fahrzeuge, 0)       AS anzahl_fahrzeuge,
                   COUNT(v.reservation_id)               AS anzahl_fahrten,
                   ROUND(COALESCE(SUM(v.nutzung_h), 0), 2) AS nutzungsstunden,
                   COALESCE(SUM(v.km), 0)                AS km,
                   ROUND(100 * COALESCE(SUM(v.nutzung_h), 0)
                         / NULLIF(COALESCE(b.anzahl_fahrzeuge, 0) * :stundenImZeitraum, 0), 2) AS auslastung_prozent
            FROM standort s
            LEFT JOIN bestand b ON b.standort_id = s.id
            LEFT JOIN v_fahrt_nutzung v
                   ON v.standort_id = s.id
                  AND v.beginn >= :von AND v.beginn < :bis
            GROUP BY s.id, s.name, b.anzahl_fahrzeuge
            ORDER BY anzahl_fahrten DESC, nutzungsstunden DESC, s.name
            """;

    private static final String JE_FAHRZEUG = """
            SELECT f.id                                  AS fahrzeug_id,
                   f.kennzeichen,
                   s.name                                AS standort_name,
                   COUNT(v.reservation_id)               AS anzahl_fahrten,
                   ROUND(COALESCE(SUM(v.nutzung_h), 0), 2) AS nutzungsstunden,
                   COALESCE(SUM(v.km), 0)                AS km,
                   ROUND(100 * COALESCE(SUM(v.nutzung_h), 0) / :stundenImZeitraum, 2) AS auslastung_prozent
            FROM fahrzeug f
            JOIN standort s ON s.id = f.standort_id
            LEFT JOIN v_fahrt_nutzung v
                   ON v.fahrzeug_id = f.id
                  AND v.beginn >= :von AND v.beginn < :bis
            WHERE (:standortId IS NULL OR f.standort_id = :standortId)
            GROUP BY f.id, f.kennzeichen, s.name
            ORDER BY nutzungsstunden DESC, f.kennzeichen
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public NutzungRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Fahrzeugbestand = Fahrzeuge in Betrieb, die heute dem Standort zugeordnet sind. */
    public List<StandortNutzung> jeStandort(OffsetDateTime von, OffsetDateTime bis, BigDecimal stundenImZeitraum) {
        MapSqlParameterSource parameter = new MapSqlParameterSource()
                .addValue("von", von)
                .addValue("bis", bis)
                .addValue("stundenImZeitraum", stundenImZeitraum);
        return jdbc.query(JE_STANDORT, parameter, (rs, zeile) -> new StandortNutzung(
                rs.getLong("standort_id"),
                rs.getString("standort_name"),
                rs.getLong("anzahl_fahrzeuge"),
                rs.getLong("anzahl_fahrten"),
                rs.getBigDecimal("nutzungsstunden"),
                rs.getLong("km"),
                rs.getBigDecimal("auslastung_prozent")));
    }

    /** standortId optional: aktueller Standort des Fahrzeugs (für die Flottenplanung). */
    public List<FahrzeugNutzung> jeFahrzeug(OffsetDateTime von, OffsetDateTime bis, BigDecimal stundenImZeitraum,
                                           Long standortId) {
        MapSqlParameterSource parameter = new MapSqlParameterSource()
                .addValue("von", von)
                .addValue("bis", bis)
                .addValue("stundenImZeitraum", stundenImZeitraum)
                .addValue("standortId", standortId, Types.BIGINT);
        return jdbc.query(JE_FAHRZEUG, parameter, (rs, zeile) -> new FahrzeugNutzung(
                rs.getLong("fahrzeug_id"),
                rs.getString("kennzeichen"),
                rs.getString("standort_name"),
                rs.getLong("anzahl_fahrten"),
                rs.getBigDecimal("nutzungsstunden"),
                rs.getLong("km"),
                rs.getBigDecimal("auslastung_prozent")));
    }
}
