package ch.hftm.seelandmobil.kunde;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "kunde")
public class Kunde {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false)
    private boolean gesperrt;

    protected Kunde() {
    }

    public Kunde(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public void aendern(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public void sperren() {
        this.gesperrt = true;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public boolean isGesperrt() {
        return gesperrt;
    }
}
