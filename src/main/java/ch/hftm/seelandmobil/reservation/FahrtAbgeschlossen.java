package ch.hftm.seelandmobil.reservation;

/** Wird beim Fahrtabschluss (A5) innerhalb der Transaktion veröffentlicht. */
public record FahrtAbgeschlossen(Long reservationId) {
}
