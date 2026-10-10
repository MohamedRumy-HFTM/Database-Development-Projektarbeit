package ch.hftm.seelandmobil.fahrzeug;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "fahrzeug")
public class Fahrzeug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String kennzeichen;

    @Column(nullable = false, length = 60)
    private String modell;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Kategorie kategorie;

    @Column(name = "km_stand", nullable = false)
    private int kmStand;

    @Column(name = "in_betrieb", nullable = false)
    private boolean inBetrieb = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "standort_id", nullable = false)
    private Standort standort;

    /** Optimistisches Locking: verhindert, dass parallele Änderungen sich unbemerkt überschreiben. */
    @Version
    private long version;

    protected Fahrzeug() {
    }

    public Fahrzeug(String kennzeichen, String modell, Kategorie kategorie, int kmStand, Standort standort) {
        this.kennzeichen = kennzeichen;
        this.modell = modell;
        this.kategorie = kategorie;
        this.kmStand = kmStand;
        this.standort = standort;
    }

    public void aendern(String kennzeichen, String modell, Kategorie kategorie, Standort standort) {
        this.kennzeichen = kennzeichen;
        this.modell = modell;
        this.kategorie = kategorie;
        this.standort = standort;
    }

    public void ausserBetriebNehmen() {
        this.inBetrieb = false;
    }

    public Long getId() {
        return id;
    }

    public String getKennzeichen() {
        return kennzeichen;
    }

    public String getModell() {
        return modell;
    }

    public Kategorie getKategorie() {
        return kategorie;
    }

    public int getKmStand() {
        return kmStand;
    }

    public boolean isInBetrieb() {
        return inBetrieb;
    }

    public Standort getStandort() {
        return standort;
    }

    public long getVersion() {
        return version;
    }
}
