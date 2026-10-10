package ch.hftm.seelandmobil.fahrzeug;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FahrzeugRepository extends JpaRepository<Fahrzeug, Long> {

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
