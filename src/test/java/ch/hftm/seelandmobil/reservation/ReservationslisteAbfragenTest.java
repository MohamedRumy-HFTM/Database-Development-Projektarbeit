package ch.hftm.seelandmobil.reservation;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A4 Abfrageverhalten.
 * <ul>
 *   <li>T11: Als einfache Entity-Liste lädt Hibernate Fahrzeug und Standort pro Zeile nach (N+1).
 *   Mit Fetch Join bleibt es bei einer Abfrage plus Count, unabhängig von der Seitengrösse.</li>
 *   <li>T7: Das mitgeschnittene SQL zeigt Filter, Sortierung und Seitenbegrenzung in der Datenbank.</li>
 * </ul>
 * Testdaten: ein Kunde mit 60 Reservationen auf 60 Fahrzeugen, verteilt auf alle 8 Standorte.
 * Ohne @Transactional, damit jede Messung mit leerem Persistenzkontext beginnt.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=ch.hftm.seelandmobil.reservation.SqlMitschnitt",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
})
@Import(TestcontainersConfiguration.class)
class ReservationslisteAbfragenTest {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationslisteAbfragenTest.class);

    @Autowired
    private ReservationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    private Statistics statistik;
    private long kundeId;

    @BeforeEach
    void testdaten() {
        statistik = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        kundeId = jdbc.queryForObject(
                "insert into kunde (name, email) values ('Vielfahrer', 'vielfahrer@example.ch') returning id", Long.class);
        List<Long> standortIds = jdbc.queryForList("select id from standort order by id", Long.class);
        for (int i = 0; i < 60; i++) {
            long standortId = standortIds.get(i % standortIds.size());
            long fahrzeugId = jdbc.queryForObject("""
                    insert into fahrzeug (kennzeichen, modell, kategorie, standort_id)
                    values (?, 'Skoda Fabia', 'KLEIN', ?) returning id
                    """, Long.class, "BE " + (7000 + i), standortId);
            jdbc.update("""
                    insert into reservation (kunde_id, fahrzeug_id, standort_id, beginn, ende)
                    values (?, ?, ?, timestamptz '2040-01-01 10:00+01' + make_interval(days => ?),
                                     timestamptz '2040-01-01 12:00+01' + make_interval(days => ?))
                    """, kundeId, fahrzeugId, standortId, i, i);
        }
    }

    @AfterEach
    void aufraeumen() {
        jdbc.update("delete from reservation where kunde_id = ?", kundeId);
        jdbc.update("delete from fahrzeug where kennzeichen between 'BE 7000' and 'BE 7059'");
        jdbc.update("delete from kunde where id = ?", kundeId);
    }

    @Test
    void entityListeLaedtProZeileNachFetchJoinNicht() {
        int[] groessen = {5, 20, 50};
        long[] erwartetEntityListe = {
                2 + 5 + 5,   // Abfrage + Count + 5 Fahrzeuge + 5 verschiedene Standorte
                2 + 20 + 8,  // … + 20 Fahrzeuge + alle 8 Standorte
                2 + 50 + 8};
        for (int i = 0; i < groessen.length; i++) {
            long entityListe = abfragenEntityListe(groessen[i]);
            long fetchJoin = abfragenMitFetchJoin(groessen[i]);
            LOG.info("T11 A4, Seitengrösse {}: Entity-Liste {} Abfragen, Fetch Join {} Abfragen",
                    groessen[i], entityListe, fetchJoin);

            assertThat(entityListe).as("Entity-Liste, Seitengrösse %d", groessen[i]).isEqualTo(erwartetEntityListe[i]);
            assertThat(fetchJoin).as("Fetch Join, Seitengrösse %d", groessen[i]).isEqualTo(2);
        }
    }

    @Test
    void filterSortierungUndSeitenbegrenzungLaufenInDerDatenbank() {
        SqlMitschnitt.leeren();

        service.vonKunde(kundeId, ReservationStatus.AKTIV, 1, 20);

        String abfrage = SqlMitschnitt.alle().stream()
                .filter(sql -> sql.contains("join fahrzeug") && !sql.contains("count("))
                .findFirst()
                .orElseThrow();
        LOG.info("T7 A4, SQL Seite 2: {}", abfrage);
        assertThat(abfrage)
                .contains("join standort")
                .containsPattern("where \\w+\\.kunde_id=\\? and \\w+\\.status=\\?")
                .containsPattern("order by \\w+\\.beginn desc,\\w+\\.id desc")
                .contains("offset ? rows fetch first ? rows only");
    }

    /** Wie A4 ohne Fetch Join: Seite und Count laden, dann wie der Service in DTOs umwandeln. */
    private long abfragenEntityListe(int groesse) {
        TransactionTemplate lesend = new TransactionTemplate(transactionManager);
        lesend.setReadOnly(true);
        statistik.clear();
        lesend.executeWithoutResult(status -> {
            List<Reservation> seite = entityManager.createQuery("""
                            select r from Reservation r
                            where r.kunde.id = :kundeId
                            order by r.beginn desc, r.id desc
                            """, Reservation.class)
                    .setParameter("kundeId", kundeId)
                    .setMaxResults(groesse)
                    .getResultList();
            entityManager.createQuery("select count(r) from Reservation r where r.kunde.id = :kundeId", Long.class)
                    .setParameter("kundeId", kundeId)
                    .getSingleResult();
            seite.forEach(ReservationResponse::von);
        });
        return statistik.getPrepareStatementCount();
    }

    private long abfragenMitFetchJoin(int groesse) {
        statistik.clear();
        service.vonKunde(kundeId, null, 0, groesse);
        return statistik.getPrepareStatementCount();
    }
}
