package ch.hftm.seelandmobil.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Abgeschlossene Fahrt zu einer Reservation (1:0..1). Entsteht erst bei der Rückgabe.
 */
@Entity
@Table(name = "fahrt")
public class Fahrt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(name = "km_start", nullable = false)
    private int kmStart;

    @Column(name = "km_ende", nullable = false)
    private int kmEnde;

    @Column(name = "rueckgabe_am", nullable = false)
    private OffsetDateTime rueckgabeAm;

    protected Fahrt() {
    }

    public Fahrt(Reservation reservation, int kmStart, int kmEnde, OffsetDateTime rueckgabeAm) {
        this.reservation = reservation;
        this.kmStart = kmStart;
        this.kmEnde = kmEnde;
        this.rueckgabeAm = rueckgabeAm;
    }

    public Long getId() {
        return id;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public int getKmStart() {
        return kmStart;
    }

    public int getKmEnde() {
        return kmEnde;
    }

    public OffsetDateTime getRueckgabeAm() {
        return rueckgabeAm;
    }
}
