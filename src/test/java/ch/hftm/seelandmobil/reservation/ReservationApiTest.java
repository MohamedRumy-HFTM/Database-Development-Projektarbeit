package ch.hftm.seelandmobil.reservation;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
import ch.hftm.seelandmobil.fahrzeug.Fahrzeug;
import ch.hftm.seelandmobil.fahrzeug.FahrzeugRepository;
import ch.hftm.seelandmobil.fahrzeug.Kategorie;
import ch.hftm.seelandmobil.kunde.Kunde;
import ch.hftm.seelandmobil.kunde.KundeRepository;
import ch.hftm.seelandmobil.standort.Standort;
import ch.hftm.seelandmobil.standort.StandortRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A3 Reservieren und stornieren: die Prüffälle aus dem Projektsteckbrief.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class ReservationApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StandortRepository standorte;

    @Autowired
    private FahrzeugRepository fahrzeuge;

    @Autowired
    private KundeRepository kunden;

    @Autowired
    private ReservationRepository reservationen;

    private Fahrzeug fahrzeug;
    private Kunde kunde;

    @BeforeEach
    void testdaten() {
        Standort lyss = standorte.findAll().stream()
                .filter(s -> s.getName().equals("Lyss Zentrum"))
                .findFirst()
                .orElseThrow();
        fahrzeug = fahrzeuge.save(new Fahrzeug("BE 2001", "VW Polo", Kategorie.KLEIN, 8000, lyss));
        kunde = kunden.save(new Kunde("Lea Gerber", "lea.gerber@example.ch"));
    }

    @Test
    void reservationErhaeltStandortDesFahrzeugsAlsAbholstandort() throws Exception {
        long id = reservieren(um(10), um(14));

        mvc.perform(get("/api/reservationen/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kennzeichen").value("BE 2001"))
                .andExpect(jsonPath("$.standortName").value("Lyss Zentrum"))
                .andExpect(jsonPath("$.status").value("AKTIV"));
    }

    @Test
    void ueberschneidungGibt409() throws Exception {
        reservieren(um(10), um(14));

        postReservation(fahrzeug.getId(), um(12), um(16))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Das Fahrzeug ist im gewünschten Zeitraum bereits reserviert."));
    }

    @Test
    void direktAnschliessenderZeitraumIstErlaubt() throws Exception {
        reservieren(um(10), um(14));
        reservieren(um(14), um(16));
    }

    @Test
    void stornoGibtDenZeitraumFrei() throws Exception {
        long id = reservieren(um(10), um(14));

        mvc.perform(post("/api/reservationen/{id}/stornieren", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STORNIERT"));

        reservieren(um(12), um(16));
    }

    @Test
    void zweitesStornoGibt409() throws Exception {
        long id = reservieren(um(10), um(14));
        mvc.perform(post("/api/reservationen/{id}/stornieren", id)).andExpect(status().isOk());

        mvc.perform(post("/api/reservationen/{id}/stornieren", id))
                .andExpect(status().isConflict());
    }

    @Test
    void stornoNachBeginnGibt409() throws Exception {
        OffsetDateTime beginn = OffsetDateTime.now().minusHours(1);
        Reservation laufend = reservationen.saveAndFlush(
                new Reservation(kunde, fahrzeug, beginn, beginn.plusHours(4)));

        mvc.perform(post("/api/reservationen/{id}/stornieren", laufend.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Die Reservation hat bereits begonnen und kann nicht mehr storniert werden."));
    }

    @Test
    void endeVorOderGleichBeginnGibt400() throws Exception {
        postReservation(fahrzeug.getId(), um(14), um(14))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Das Ende muss nach dem Beginn liegen."));
    }

    @Test
    void beginnInDerVergangenheitGibt400() throws Exception {
        OffsetDateTime gestern = OffsetDateTime.now().minusDays(1);

        postReservation(fahrzeug.getId(), gestern, gestern.plusHours(2))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gesperrterKundeGibt409() throws Exception {
        kunde.sperren();
        kunden.saveAndFlush(kunde);

        postReservation(fahrzeug.getId(), um(10), um(14))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Der Kunde ist gesperrt und kann keine Reservation anlegen."));
    }

    @Test
    void fahrzeugAusserBetriebGibt409() throws Exception {
        fahrzeug.ausserBetriebNehmen();
        fahrzeuge.saveAndFlush(fahrzeug);

        postReservation(fahrzeug.getId(), um(10), um(14))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Das Fahrzeug ist ausser Betrieb."));
    }

    @Test
    void unbekanntesFahrzeugGibt404() throws Exception {
        postReservation(999_999L, um(10), um(14))
                .andExpect(status().isNotFound());
    }

    /** Ein Zeitpunkt am 5. März 2040, damit die Tests nicht von heute abhängen. */
    private static OffsetDateTime um(int stunde) {
        return OffsetDateTime.of(2040, 3, 5, stunde, 0, 0, 0, ZoneOffset.ofHours(1));
    }

    private long reservieren(OffsetDateTime beginn, OffsetDateTime ende) throws Exception {
        String antwort = postReservation(fahrzeug.getId(), beginn, ende)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(antwort, "$.id")).longValue();
    }

    private ResultActions postReservation(long fahrzeugId, OffsetDateTime beginn, OffsetDateTime ende)
            throws Exception {
        String json = """
                {"kundeId": %d, "fahrzeugId": %d, "beginn": "%s", "ende": "%s"}
                """.formatted(kunde.getId(), fahrzeugId, beginn, ende);
        return mvc.perform(post("/api/reservationen").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
