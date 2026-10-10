package ch.hftm.seelandmobil.common;

/** Gesuchter Datensatz existiert nicht → 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
