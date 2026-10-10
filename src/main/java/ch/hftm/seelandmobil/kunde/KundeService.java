package ch.hftm.seelandmobil.kunde;

import ch.hftm.seelandmobil.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private Kunde laden(Long id) {
        return kunden.findById(id)
                .orElseThrow(() -> new NotFoundException("Kunde " + id + " existiert nicht."));
    }
}
