package ch.hftm.seelandmobil.common;

/** Anfrage verletzt eine Geschäftsregel oder den aktuellen Datenbestand → 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
