package ch.hftm.seelandmobil.standort;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Standort, an dem Fahrzeuge stationiert sind. Die Standorte sind fix vorgegeben (Migration V6).
 */
@Entity
@Table(name = "standort")
public class Standort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 60)
    private String ort;

    protected Standort() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOrt() {
        return ort;
    }
}
