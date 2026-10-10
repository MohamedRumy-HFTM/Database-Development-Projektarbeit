package ch.hftm.seelandmobil.kunde;

import ch.hftm.seelandmobil.common.NotFoundException;
import ch.hftm.seelandmobil.common.Seite;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional
public class KundeService {

    private final KundeRepository kunden;

    public KundeService(KundeRepository kunden) {
        this.kunden = kunden;
    }

    public KundeResponse erfassen(KundeRequest request) {
        Kunde kunde = new Kunde(request.name().strip(), request.email().strip());
        return KundeResponse.von(kunden.saveAndFlush(kunde));
    }

    @Transactional(readOnly = true)
    public KundeResponse lesen(Long id) {
        return KundeResponse.von(laden(id));
    }

    /** Ohne Suchbegriff alle Kunden, sortiert nach Name. */
    @Transactional(readOnly = true)
    public Seite<KundeResponse> suchen(String suche, int seite, int groesse) {
        String muster = suche == null || suche.isBlank()
                ? "%"
                : "%" + maskieren(suche.strip().toLowerCase(Locale.ROOT)) + "%";
        return Seite.von(kunden.suche(muster, PageRequest.of(seite, groesse)).map(KundeResponse::von));
    }

    /** % und _ aus der Eingabe gelten wörtlich, nicht als Platzhalter. */
    private static String maskieren(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public KundeResponse aendern(Long id, KundeRequest request) {
        Kunde kunde = laden(id);
        kunde.aendern(request.name().strip(), request.email().strip());
        return KundeResponse.von(kunden.saveAndFlush(kunde));
    }

    /** Gesperrte Kunden können keine neuen Reservationen mehr anlegen (A3). */
    public KundeResponse sperren(Long id) {
        Kunde kunde = laden(id);
        kunde.sperren();
        return KundeResponse.von(kunden.saveAndFlush(kunde));
    }

    private Kunde laden(Long id) {
        return kunden.findById(id)
                .orElseThrow(() -> new NotFoundException("Kunde " + id + " existiert nicht."));
    }
}
