package ch.hftm.seelandmobil.db;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
import ch.hftm.seelandmobil.fahrzeug.Fahrzeug;
import ch.hftm.seelandmobil.fahrzeug.FahrzeugRepository;
import ch.hftm.seelandmobil.fahrzeug.Kategorie;
import ch.hftm.seelandmobil.kunde.Kunde;
import ch.hftm.seelandmobil.kunde.KundeRepository;
import ch.hftm.seelandmobil.reservation.Fahrt;
import ch.hftm.seelandmobil.reservation.FahrtRepository;
import ch.hftm.seelandmobil.reservation.Reservation;
import ch.hftm.seelandmobil.reservation.ReservationRepository;
import ch.hftm.seelandmobil.reservation.ReservationStatus;
import ch.hftm.seelandmobil.standort.Standort;
import ch.hftm.seelandmobil.standort.StandortRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Entities passen zum Flyway-Schema: Hibernate validiert beim Start (ddl-auto=validate),
 * und ein vollständiger Datensatz lässt sich speichern und wieder lesen.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class EntityMappingTest {

    @Autowired
    private StandortRepository standorte;

    @Autowired
    private FahrzeugRepository fahrzeuge;

    @Autowired
    private KundeRepository kunden;

    @Autowired
    private ReservationRepository reservationen;

    @Autowired
    private FahrtRepository fahrten;

    @Autowired
    private EntityManager entityManager;

    @Test
    void reservationMitFahrtWirdGespeichertUndGelesen() {
        Standort lyss = standorte.findAll().stream()
                .filter(s -> s.getName().equals("Lyss Zentrum"))
                .findFirst()
                .orElseThrow();
        Fahrzeug fahrzeug = fahrzeuge.save(new Fahrzeug("BE 12345", "VW Polo", Kategorie.KLEIN, 12000, lyss));
        Kunde kunde = kunden.save(new Kunde("Anna Muster", "anna@test.ch"));
        Reservation reservation = reservationen.save(new Reservation(kunde, fahrzeug,
                OffsetDateTime.parse("2026-09-01T10:00:00+02:00"),
                OffsetDateTime.parse("2026-09-01T14:00:00+02:00")));
        fahrten.save(new Fahrt(reservation, 12000, 12080, OffsetDateTime.parse("2026-09-01T13:30:00+02:00")));
        entityManager.flush();
        entityManager.clear();

        Reservation gelesen = reservationen.findById(reservation.getId()).orElseThrow();
        Fahrzeug fahrzeugGelesen = fahrzeuge.findById(fahrzeug.getId()).orElseThrow();

        assertThat(gelesen.getStatus()).isEqualTo(ReservationStatus.AKTIV);
        assertThat(gelesen.getStandort().getName()).isEqualTo("Lyss Zentrum");
        assertThat(gelesen.getBeginn()).isAtSameInstantAs(OffsetDateTime.parse("2026-09-01T10:00:00+02:00"));
        assertThat(gelesen.getErstelltAm()).isNotNull();
        assertThat(fahrzeugGelesen.isInBetrieb()).isTrue();
        assertThat(fahrzeugGelesen.getVersion()).isZero();
        assertThat(reservationen.existsByFahrzeugIdAndStatus(fahrzeug.getId(), ReservationStatus.AKTIV)).isTrue();
    }
}
