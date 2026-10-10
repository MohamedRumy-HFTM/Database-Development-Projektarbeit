package ch.hftm.seelandmobil.fahrzeug;

import ch.hftm.seelandmobil.common.ConflictException;
import ch.hftm.seelandmobil.common.NotFoundException;
import ch.hftm.seelandmobil.reservation.ReservationRepository;
import ch.hftm.seelandmobil.reservation.ReservationStatus;
import ch.hftm.seelandmobil.standort.Standort;
import ch.hftm.seelandmobil.standort.StandortRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FahrzeugService {

    private final FahrzeugRepository fahrzeuge;
    private final StandortRepository standorte;
    private final ReservationRepository reservationen;

    public FahrzeugService(FahrzeugRepository fahrzeuge, StandortRepository standorte,
                           ReservationRepository reservationen) {
        this.fahrzeuge = fahrzeuge;
        this.standorte = standorte;
        this.reservationen = reservationen;
    }

    public FahrzeugResponse erfassen(FahrzeugRequest request) {
        Standort standort = standortLaden(request.standortId());
        Fahrzeug fahrzeug = new Fahrzeug(request.kennzeichen(), request.modell(), request.kategorie(),
                request.kmStand(), standort);
        return FahrzeugResponse.von(fahrzeuge.saveAndFlush(fahrzeug));
    }

    @Transactional(readOnly = true)
    public FahrzeugResponse lesen(Long id) {
        return FahrzeugResponse.von(laden(id));
    }

    public FahrzeugResponse aendern(Long id, FahrzeugAenderung aenderung) {
        Fahrzeug fahrzeug = ladenZumAendern(id);
        if (fahrzeug.getVersion() != aenderung.version()) {
            throw new ConflictException("Das Fahrzeug wurde zwischenzeitlich geändert. Bitte neu laden.");
        }
        Standort standort = standortLaden(aenderung.standortId());
        boolean standortwechsel = !standort.getId().equals(fahrzeug.getStandort().getId());
        if (standortwechsel && hatOffeneReservationen(id)) {
            throw new ConflictException("Standortwechsel nicht möglich: Das Fahrzeug hat offene Reservationen.");
        }
        fahrzeug.aendern(aenderung.kennzeichen(), aenderung.modell(), aenderung.kategorie(), standort);
        return FahrzeugResponse.von(fahrzeuge.saveAndFlush(fahrzeug));
    }

    public FahrzeugResponse ausserBetriebNehmen(Long id) {
        Fahrzeug fahrzeug = ladenZumAendern(id);
        if (hatOffeneReservationen(id)) {
            throw new ConflictException("Ausserbetriebnahme nicht möglich: Das Fahrzeug hat offene Reservationen.");
        }
        fahrzeug.ausserBetriebNehmen();
        return FahrzeugResponse.von(fahrzeuge.saveAndFlush(fahrzeug));
    }

    private boolean hatOffeneReservationen(Long fahrzeugId) {
        return reservationen.existsByFahrzeugIdAndStatus(fahrzeugId, ReservationStatus.AKTIV);
    }

    private Fahrzeug laden(Long id) {
        return fahrzeuge.findById(id)
                .orElseThrow(() -> new NotFoundException("Fahrzeug " + id + " existiert nicht."));
    }

    /** Sperrt das Fahrzeug, damit während der Prüfung keine neue Reservation entsteht (siehe A3). */
    private Fahrzeug ladenZumAendern(Long id) {
        return fahrzeuge.findByIdZumAendern(id)
                .orElseThrow(() -> new NotFoundException("Fahrzeug " + id + " existiert nicht."));
    }

    private Standort standortLaden(Long id) {
        return standorte.findById(id)
                .orElseThrow(() -> new NotFoundException("Standort " + id + " existiert nicht."));
    }
}
