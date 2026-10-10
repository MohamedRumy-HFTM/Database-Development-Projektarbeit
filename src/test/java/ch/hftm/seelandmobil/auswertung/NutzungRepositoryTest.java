package ch.hftm.seelandmobil.auswertung;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A7 per JDBC (T6) mit einem kontrollierten Datensatz. Zeitraum: März 2040 (UTC), 744 Stunden.
 * <pre>
 * Nr  Fahrzeug  Abholstandort  Beginn (UTC)       Status          Fahrt              zählt?
 *  1  BE 9101   Aarberg        02.03. 08:00       ABGESCHLOSSEN   2.5 h,  60 km      ja
 *  2  BE 9101   Aarberg        10.03. 09:00       ABGESCHLOSSEN   4.0 h,  80 km      ja
 *  3  BE 9101   Aarberg        20.03. 09:00       STORNIERT       –                  nein
 *  4  BE 9101   Aarberg        28.02. 09:00       ABGESCHLOSSEN   3.0 h,  60 km      nein, vor dem Zeitraum
 *  5  BE 9101   Aarberg        25.03. 09:00       AKTIV           –                  nein, offen
 *  6  BE 9102   Aarberg        15.03. 10:00       ABGESCHLOSSEN   1.0 h,  20 km      ja
 *  7  BE 9102   Aarberg        31.03. 23:00       ABGESCHLOSSEN   3.0 h,  10 km      ja, Beginn im Zeitraum
 *  8  BE 9103   Ins            01.04. 00:00       ABGESCHLOSSEN   2.0 h,  25 km      nein, Beginn = bis
 *  9  BE 9104   Aarberg        05.03. 08:00       ABGESCHLOSSEN   2.0 h,  30 km      ja, für Aarberg
 * </pre>
 * BE 9104 steht heute in Ins, wurde aber in Aarberg abgeholt: Die Fahrt zählt für Aarberg.
 * Erwartet für Aarberg: 5 Fahrten, 12.5 h, 200 km. Ins und alle übrigen Standorte: 0.
 */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class NutzungRepositoryTest {

    private static final OffsetDateTime VON = OffsetDateTime.parse("2040-03-01T00:00:00Z");
    private static final OffsetDateTime BIS = OffsetDateTime.parse("2040-04-01T00:00:00Z");
    private static final BigDecimal STUNDEN_IM_MAERZ = BigDecimal.valueOf(31 * 24);

    @Autowired
    private NutzungRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private long aarberg;
    private long ins;
    private long kunde;

    @BeforeEach
    void testdaten() {
        aarberg = standortId("Aarberg");
        ins = standortId("Ins");
        kunde = jdbc.queryForObject(
                "insert into kunde (name, email) values ('Lara Brunner', 'lara.brunner@example.ch') returning id",
                Long.class);
        long a = fahrzeug("BE 9101", aarberg);
        long b = fahrzeug("BE 9102", aarberg);
        long c = fahrzeug("BE 9103", ins);
        long d = fahrzeug("BE 9104", ins);

        fahrt(reservation(a, aarberg, "2040-03-02 08:00Z", "2040-03-02 12:00Z", "ABGESCHLOSSEN"), 100, 160, "2040-03-02 10:30Z");
        fahrt(reservation(a, aarberg, "2040-03-10 09:00Z", "2040-03-10 14:00Z", "ABGESCHLOSSEN"), 160, 240, "2040-03-10 13:00Z");
        reservation(a, aarberg, "2040-03-20 09:00Z", "2040-03-20 12:00Z", "STORNIERT");
        fahrt(reservation(a, aarberg, "2040-02-28 09:00Z", "2040-02-28 12:00Z", "ABGESCHLOSSEN"), 40, 100, "2040-02-28 12:00Z");
        reservation(a, aarberg, "2040-03-25 09:00Z", "2040-03-25 12:00Z", "AKTIV");
        fahrt(reservation(b, aarberg, "2040-03-15 10:00Z", "2040-03-15 12:00Z", "ABGESCHLOSSEN"), 50, 70, "2040-03-15 11:00Z");
        fahrt(reservation(b, aarberg, "2040-03-31 23:00Z", "2040-04-01 03:00Z", "ABGESCHLOSSEN"), 70, 80, "2040-04-01 02:00Z");
        fahrt(reservation(c, ins, "2040-04-01 00:00Z", "2040-04-01 02:00Z", "ABGESCHLOSSEN"), 0, 25, "2040-04-01 02:00Z");
        fahrt(reservation(d, aarberg, "2040-03-05 08:00Z", "2040-03-05 11:00Z", "ABGESCHLOSSEN"), 300, 330, "2040-03-05 10:00Z");
    }

    @Test
    void jeStandortExakteSummen() {
        Map<String, StandortNutzung> ergebnis = nachName(repository.jeStandort(VON, BIS), StandortNutzung::standortName);

        StandortNutzung aarbergNutzung = ergebnis.get("Aarberg");
        assertThat(aarbergNutzung.anzahlFahrten()).isEqualTo(5);
        assertThat(aarbergNutzung.nutzungsstunden()).isEqualByComparingTo("12.5");
        assertThat(aarbergNutzung.km()).isEqualTo(200);
    }

    @Test
    void standorteOhneFahrtErscheinenMitNull() {
        List<StandortNutzung> ergebnis = repository.jeStandort(VON, BIS);

        assertThat(ergebnis).hasSize(8);
        StandortNutzung insNutzung = nachName(ergebnis, StandortNutzung::standortName).get("Ins");
        assertThat(insNutzung.anzahlFahrten()).as("COUNT(*) ergäbe hier 1").isZero();
        assertThat(insNutzung.nutzungsstunden()).isEqualByComparingTo("0");
        assertThat(insNutzung.km()).isZero();
        assertThat(ergebnis.get(0).standortName()).as("meiste Fahrten zuerst").isEqualTo("Aarberg");
    }

    @Test
    void jeFahrzeugMitAuslastung() {
        Map<String, FahrzeugNutzung> ergebnis = nachName(
                repository.jeFahrzeug(VON, BIS, STUNDEN_IM_MAERZ, null), FahrzeugNutzung::kennzeichen);

        pruefe(ergebnis.get("BE 9101"), 2, "6.5", 140, "0.87");   // 6.5 h ÷ 744 h
        pruefe(ergebnis.get("BE 9102"), 2, "4.0", 30, "0.54");
        pruefe(ergebnis.get("BE 9103"), 0, "0", 0, "0");
        pruefe(ergebnis.get("BE 9104"), 1, "2.0", 30, "0.27");
    }

    @Test
    void jeFahrzeugNachAktuellemStandortGefiltert() {
        List<String> kennzeichen = repository.jeFahrzeug(VON, BIS, STUNDEN_IM_MAERZ, ins).stream()
                .map(FahrzeugNutzung::kennzeichen)
                .toList();

        assertThat(kennzeichen).contains("BE 9103", "BE 9104").doesNotContain("BE 9101", "BE 9102");
    }

    private static void pruefe(FahrzeugNutzung nutzung, long fahrten, String stunden, long km, String auslastung) {
        assertThat(nutzung.anzahlFahrten()).as(nutzung.kennzeichen()).isEqualTo(fahrten);
        assertThat(nutzung.nutzungsstunden()).as(nutzung.kennzeichen()).isEqualByComparingTo(stunden);
        assertThat(nutzung.km()).as(nutzung.kennzeichen()).isEqualTo(km);
        assertThat(nutzung.auslastungProzent()).as(nutzung.kennzeichen()).isEqualByComparingTo(auslastung);
    }

    private static <T> Map<String, T> nachName(List<T> liste, Function<T, String> name) {
        return liste.stream().collect(Collectors.toMap(name, Function.identity()));
    }

    private long standortId(String name) {
        return jdbc.queryForObject("select id from standort where name = ?", Long.class, name);
    }

    private long fahrzeug(String kennzeichen, long standortId) {
        return jdbc.queryForObject("""
                insert into fahrzeug (kennzeichen, modell, kategorie, standort_id)
                values (?, 'Hyundai i20', 'KLEIN', ?) returning id
                """, Long.class, kennzeichen, standortId);
    }

    private long reservation(long fahrzeugId, long standortId, String beginn, String ende, String status) {
        return jdbc.queryForObject("""
                insert into reservation (kunde_id, fahrzeug_id, standort_id, beginn, ende, status)
                values (?, ?, ?, ?::timestamptz, ?::timestamptz, ?) returning id
                """, Long.class, kunde, fahrzeugId, standortId, beginn, ende, status);
    }

    private void fahrt(long reservationId, int kmStart, int kmEnde, String rueckgabe) {
        jdbc.update("""
                insert into fahrt (reservation_id, km_start, km_ende, rueckgabe_am)
                values (?, ?, ?, ?::timestamptz)
                """, reservationId, kmStart, kmEnde, rueckgabe);
    }
}
