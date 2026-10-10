package ch.hftm.seelandmobil.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByFahrzeugIdAndStatus(Long fahrzeugId, ReservationStatus status);

    /** Exklusive Zeilensperre, damit Storno und Fahrtabschluss den Status nicht gegenseitig überschreiben. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdZumAendern(@Param("id") Long id);
}
