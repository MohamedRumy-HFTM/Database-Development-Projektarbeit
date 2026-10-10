package ch.hftm.seelandmobil.reservation;

import ch.hftm.seelandmobil.common.ConflictException;
import ch.hftm.seelandmobil.common.NotFoundException;
import ch.hftm.seelandmobil.common.Seite;
import ch.hftm.seelandmobil.common.UngueltigeAnfrageException;
import ch.hftm.seelandmobil.fahrzeug.Fahrzeug;
import ch.hftm.seelandmobil.fahrzeug.FahrzeugRepository;
import ch.hftm.seelandmobil.kunde.Kunde;
import ch.hftm.seelandmobil.kunde.KundeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
@Transactional
public class ReservationService {

    private final ReservationRepository reservationen;
    private final KundeRepository kunden;
    private final FahrzeugRepository fahrzeuge;
    private final Clock clock;

    public ReservationService(ReservationRepository reservationen, KundeRepository kunden,
                              FahrzeugRepository fahrzeuge, Clock clock) {
        this.reservationen = reservationen;
        this.kunden = kunden;
        this.fahrzeuge = fahrzeuge;
        this.clock = clock;
    }

    /**
     * Überschneidungen prüft bewusst nicht Java, sondern der Exclusion-Constraint
     * ex_reservation_ueberschneidung: Nur er ist auch bei gleichzeitigen Anfragen zuverlässig.
     */
    public ReservationResponse reservieren(ReservationRequest request) {
        if (!request.ende().isAfter(request.beginn())) {
            throw new UngueltigeAnfrageException("Das Ende muss nach dem Beginn liegen.");
        }
        if (request.beginn().isBefore(OffsetDateTime.now(clock))) {
            throw new UngueltigeAnfrageException("Der Beginn darf nicht in der Vergangenheit liegen.");
        }
        Kunde kunde = kunden.findById(request.kundeId())
                .orElseThrow(() -> new NotFoundException("Kunde " + request.kundeId() + " existiert nicht."));
        Fahrzeug fahrzeug = fahrzeuge.findByIdZumReservieren(request.fahrzeugId())
                .orElseThrow(() -> new NotFoundException("Fahrzeug " + request.fahrzeugId() + " existiert nicht."));
        if (kunde.isGesperrt()) {
            throw new ConflictException("Der Kunde ist gesperrt und kann keine Reservation anlegen.");
        }
        if (!fahrzeug.isInBetrieb()) {
            throw new ConflictException("Das Fahrzeug ist ausser Betrieb.");
        }
        Reservation reservation = new Reservation(kunde, fahrzeug, request.beginn(), request.ende());
        return ReservationResponse.von(reservationen.saveAndFlush(reservation));
    }

    public ReservationResponse stornieren(Long id) {
        Reservation reservation = reservationen.findByIdZumAendern(id)
                .orElseThrow(() -> new NotFoundException("Reservation " + id + " existiert nicht."));
        if (reservation.getStatus() != ReservationStatus.AKTIV) {
            throw new ConflictException("Nur aktive Reservationen können storniert werden.");
        }
        if (!OffsetDateTime.now(clock).isBefore(reservation.getBeginn())) {
            throw new ConflictException("Die Reservation hat bereits begonnen und kann nicht mehr storniert werden.");
        }
        reservation.stornieren();
        return ReservationResponse.von(reservationen.saveAndFlush(reservation));
    }

    /**
     * A4: Filter, Sortierung und Seitenbegrenzung laufen in der Datenbank. Ob der Kunde existiert,
     * wird nur bei leerer Seite geprüft – im Normalfall bleibt es bei Abfrage + Count.
     */
    @Transactional(readOnly = true)
    public Seite<ReservationResponse> vonKunde(Long kundeId, ReservationStatus status, int seite, int groesse) {
        Pageable pageable = PageRequest.of(seite, groesse);
        Page<Reservation> treffer = status == null
                ? reservationen.findeVonKunde(kundeId, pageable)
                : reservationen.findeVonKundeMitStatus(kundeId, status, pageable);
        if (treffer.isEmpty() && !kunden.existsById(kundeId)) {
            throw new NotFoundException("Kunde " + kundeId + " existiert nicht.");
        }
        return Seite.von(treffer.map(ReservationResponse::von));
    }

    @Transactional(readOnly = true)
    public ReservationResponse lesen(Long id) {
        return ReservationResponse.von(reservationen.findById(id)
                .orElseThrow(() -> new NotFoundException("Reservation " + id + " existiert nicht.")));
    }
}
