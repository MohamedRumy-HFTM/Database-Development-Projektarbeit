package ch.hftm.seelandmobil.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByFahrzeugIdAndStatus(Long fahrzeugId, ReservationStatus status);

    /**
     * A4: Reservationen eines Kunden, neueste zuerst. Der Fetch Join lädt Fahrzeug und Abholstandort
     * in derselben Abfrage (kein N+1). id als zweites Sortierkriterium macht die Reihenfolge
     * bei gleichem Beginn stabil, sonst wären Seitengrenzen nicht eindeutig.
     */
    @Query(value = """
            select r from Reservation r
            join fetch r.fahrzeug
            join fetch r.standort
            where r.kunde.id = :kundeId
            order by r.beginn desc, r.id desc
            """,
            countQuery = "select count(r) from Reservation r where r.kunde.id = :kundeId")
    Page<Reservation> findeVonKunde(@Param("kundeId") Long kundeId, Pageable pageable);

    /** Wie {@link #findeVonKunde}, zusätzlich nach Status gefiltert. */
    @Query(value = """
            select r from Reservation r
            join fetch r.fahrzeug
            join fetch r.standort
            where r.kunde.id = :kundeId and r.status = :status
            order by r.beginn desc, r.id desc
            """,
            countQuery = "select count(r) from Reservation r where r.kunde.id = :kundeId and r.status = :status")
    Page<Reservation> findeVonKundeMitStatus(@Param("kundeId") Long kundeId,
                                             @Param("status") ReservationStatus status,
                                             Pageable pageable);

    /** Exklusive Zeilensperre, damit Storno und Fahrtabschluss den Status nicht gegenseitig überschreiben. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdZumAendern(@Param("id") Long id);

    /**
     * Bedingtes Update: ändert den Status nur, wenn er noch dem erwarteten entspricht.
     * Wird sofort in der Datenbank ausgeführt (nicht erst beim Flush) und liefert die Anzahl Zeilen.
     */
    @Modifying
    @Query("update Reservation r set r.status = :neu where r.id = :id and r.status = :erwartet")
    int statusAendern(@Param("id") Long id,
                      @Param("erwartet") ReservationStatus erwartet,
                      @Param("neu") ReservationStatus neu);
}
