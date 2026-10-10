package ch.hftm.seelandmobil.reservation;

import ch.hftm.seelandmobil.common.ConflictException;
import ch.hftm.seelandmobil.common.NotFoundException;
import ch.hftm.seelandmobil.common.UngueltigeAnfrageException;
import ch.hftm.seelandmobil.fahrzeug.Fahrzeug;
import ch.hftm.seelandmobil.fahrzeug.FahrzeugRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * A5 Fahrt abschliessen: drei Änderungen in einer Transaktion – vollständig oder gar nicht.
 */
@Service
public class FahrtService {

    private final ReservationRepository reservationen;
    private final FahrtRepository fahrten;
    private final FahrzeugRepository fahrzeuge;
    private final Clock clock;

    public FahrtService(ReservationRepository reservationen, FahrtRepository fahrten,
                        FahrzeugRepository fahrzeuge, Clock clock) {
        this.reservationen = reservationen;
        this.fahrten = fahrten;
        this.fahrzeuge = fahrzeuge;
        this.clock = clock;
    }

    @Transactional
    public FahrtResponse abschliessen(Long reservationId, FahrtAbschluss abschluss) {
        Reservation reservation = reservationen.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation " + reservationId + " existiert nicht."));
        OffsetDateTime jetzt = OffsetDateTime.now(clock);
        if (reservation.getBeginn().isAfter(jetzt)) {
            throw new ConflictException("Die Reservation hat noch nicht begonnen.");
        }
        if (!abschluss.rueckgabeAm().isAfter(reservation.getBeginn()) || abschluss.rueckgabeAm().isAfter(jetzt)) {
            throw new UngueltigeAnfrageException("Die Rückgabezeit muss nach dem Beginn und nicht in der Zukunft liegen.");
        }

        // Schritt 1: Status setzen, nur wenn die Reservation noch AKTIV ist. Das bedingte Update
        // wird sofort ausgeführt und schützt auch gegen einen gleichzeitigen zweiten Abschluss.
        int geaendert = reservationen.statusAendern(reservationId, ReservationStatus.AKTIV,
                ReservationStatus.ABGESCHLOSSEN);
        if (geaendert == 0) {
            throw new ConflictException("Die Reservation ist nicht mehr aktiv (bereits abgeschlossen oder storniert).");
        }

        // Schritt 2: Fahrt anlegen. km_ende >= km_start prüft die Datenbank (ck_fahrt_km);
        // verletzt der Wert die Regel, wird auch Schritt 1 zurückgerollt.
        Fahrzeug fahrzeug = reservation.getFahrzeug();
        Fahrt fahrt = fahrten.saveAndFlush(new Fahrt(reservation, fahrzeug.getKmStand(),
                abschluss.kmEnde(), abschluss.rueckgabeAm()));

        // Schritt 3: km-Stand nachführen. Hat jemand das Fahrzeug inzwischen geändert,
        // schlägt das Update über die version fehl (409) und alles wird zurückgerollt.
        fahrzeug.kmStandNachfuehren(abschluss.kmEnde());
        fahrzeuge.saveAndFlush(fahrzeug);

        return FahrtResponse.von(fahrt, fahrzeug.getId());
    }
}
