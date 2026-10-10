package ch.hftm.seelandmobil.fahrzeug;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface FahrzeugRepository extends JpaRepository<Fahrzeug, Long> {

    /**
     * Bedingungen der Verfügbarkeitssuche (A6), gemeinsam für Abfrage und Count.
     * Überschneidung im halboffenen Intervall: beginn < bis und ende > von.
     * STORNIERT steht als Literal im SQL, damit PostgreSQL den Index des
     * Exclusion-Constraints (WHERE status <> 'STORNIERT') verwenden kann.
     */
    String VERFUEGBAR = """
            from Fahrzeug f join f.standort s
            where f.inBetrieb = true
              and (:standortId is null or s.id = :standortId)
              and (:kategorie is null or f.kategorie = :kategorie)
              and not exists (
                  select r from Reservation r
                  where r.fahrzeug = f
                    and r.status <> ch.hftm.seelandmobil.reservation.ReservationStatus.STORNIERT
                    and r.beginn < :bis and r.ende > :von)
            """;

    /** A6: DTO-Projektion – es werden nur die benötigten Spalten gelesen, keine Entities. */
    @Query(value = """
            select new ch.hftm.seelandmobil.fahrzeug.VerfuegbaresFahrzeug(
                f.id, f.kennzeichen, f.modell, f.kategorie, s.id, s.name)
            """ + VERFUEGBAR + " order by f.kennzeichen",
            countQuery = "select count(f) " + VERFUEGBAR)
    Page<VerfuegbaresFahrzeug> findeVerfuegbare(@Param("von") OffsetDateTime von,
                                                @Param("bis") OffsetDateTime bis,
                                                @Param("standortId") Long standortId,
                                                @Param("kategorie") Kategorie kategorie,
                                                Pageable pageable);

    /**
     * Exklusive Zeilensperre (A1). Standortwechsel und Ausserbetriebnahme prüfen zuerst,
     * ob offene Reservationen existieren; bis zum Commit darf keine neue dazukommen.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Fahrzeug f where f.id = :id")
    Optional<Fahrzeug> findByIdZumAendern(@Param("id") Long id);

    /**
     * Geteilte Zeilensperre (A3). Mehrere Reservationen können gleichzeitig laufen
     * (Überschneidungen verhindert der Exclusion-Constraint), eine Änderung nach A1 wartet.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select f from Fahrzeug f where f.id = :id")
    Optional<Fahrzeug> findByIdZumReservieren(@Param("id") Long id);
}
