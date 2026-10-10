package ch.hftm.seelandmobil.common;

/** Eingabe ist in sich unzulässig, unabhängig vom Datenbestand → 400. */
public class UngueltigeAnfrageException extends RuntimeException {

    public UngueltigeAnfrageException(String message) {
        super(message);
    }
}
