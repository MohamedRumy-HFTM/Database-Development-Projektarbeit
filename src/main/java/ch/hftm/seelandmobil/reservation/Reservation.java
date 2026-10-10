package ch.hftm.seelandmobil.reservation;

import ch.hftm.seelandmobil.fahrzeug.Fahrzeug;
import ch.hftm.seelandmobil.kunde.Kunde;
import ch.hftm.seelandmobil.standort.Standort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kunde_id", nullable = false)
    private Kunde kunde;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fahrzeug_id", nullable = false)
    private Fahrzeug fahrzeug;

    /** Abholstandort: Standort des Fahrzeugs zum Zeitpunkt der Buchung. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "standort_id", nullable = false)
    private Standort standort;

    @Column(nullable = false)
    private OffsetDateTime beginn;

    @Column(nullable = false)
    private OffsetDateTime ende;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 13)
    private ReservationStatus status = ReservationStatus.AKTIV;

    @Column(name = "erstellt_am", nullable = false)
    private OffsetDateTime erstelltAm;

    protected Reservation() {
    }

    public Reservation(Kunde kunde, Fahrzeug fahrzeug, OffsetDateTime beginn, OffsetDateTime ende) {
        this.kunde = kunde;
        this.fahrzeug = fahrzeug;
        this.standort = fahrzeug.getStandort();
        this.beginn = beginn;
        this.ende = ende;
        this.erstelltAm = OffsetDateTime.now();
    }

    /** Der Datensatz bleibt erhalten; der Exclusion-Constraint ignoriert stornierte Reservationen. */
    public void stornieren() {
        this.status = ReservationStatus.STORNIERT;
    }

    public Long getId() {
        return id;
    }

    public Kunde getKunde() {
        return kunde;
    }

    public Fahrzeug getFahrzeug() {
        return fahrzeug;
    }

    public Standort getStandort() {
        return standort;
    }

    public OffsetDateTime getBeginn() {
        return beginn;
    }

    public OffsetDateTime getEnde() {
        return ende;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getErstelltAm() {
        return erstelltAm;
    }
}
