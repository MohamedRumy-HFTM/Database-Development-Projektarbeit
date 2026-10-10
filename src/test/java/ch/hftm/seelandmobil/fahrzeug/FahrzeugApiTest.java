package ch.hftm.seelandmobil.fahrzeug;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
import ch.hftm.seelandmobil.kunde.Kunde;
import ch.hftm.seelandmobil.kunde.KundeRepository;
import ch.hftm.seelandmobil.reservation.Reservation;
import ch.hftm.seelandmobil.reservation.ReservationRepository;
import ch.hftm.seelandmobil.standort.StandortRepository;
import com.jayway.jsonpath.JsonPath;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A1 Fahrzeuge verwalten: Erfassen, Lesen, Ändern und Ausserbetriebnahme über die REST-API.
 * Jeder Test läuft in einer Transaktion, die am Ende zurückgerollt wird.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class FahrzeugApiTest {

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

    @Test
    void erfasstFahrzeugUndLiefertEsAus() throws Exception {
        long id = erfassen("BE 1001", "Biel Bahnhof");

        mvc.perform(get("/api/fahrzeuge/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kennzeichen").value("BE 1001"))
                .andExpect(jsonPath("$.kategorie").value("KOMBI"))
                .andExpect(jsonPath("$.standortName").value("Biel Bahnhof"))
                .andExpect(jsonPath("$.inBetrieb").value(true))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void doppeltesKennzeichenGibt409() throws Exception {
        erfassen("BE 1002", "Biel Bahnhof");

        postFahrzeug("BE 1002", standortId("Nidau"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ein Fahrzeug mit diesem Kennzeichen existiert bereits."));
    }

    @Test
    void ungueltigesKennzeichenGibt400() throws Exception {
        postFahrzeug("be-1003", standortId("Biel Bahnhof"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fehler.kennzeichen").value("muss das Format 'BE 12345' haben"));
    }

    @Test
    void unbekannterStandortGibt404() throws Exception {
        postFahrzeug("BE 1004", 999_999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Standort 999999 existiert nicht."));
    }

    @Test
    void unbekanntesFahrzeugGibt404() throws Exception {
        mvc.perform(get("/api/fahrzeuge/{id}", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void aendernErhoehtDieVersion() throws Exception {
        long id = erfassen("BE 1005", "Biel Bahnhof");

        putFahrzeug(id, "BE 1005", "Lyss Zentrum", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modell").value("Skoda Octavia"))
                .andExpect(jsonPath("$.standortName").value("Lyss Zentrum"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void veralteteVersionGibt409() throws Exception {
        long id = erfassen("BE 1006", "Biel Bahnhof");
        putFahrzeug(id, "BE 1006", "Biel Bahnhof", 0).andExpect(status().isOk());

        putFahrzeug(id, "BE 1006", "Nidau", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Das Fahrzeug wurde zwischenzeitlich geändert. Bitte neu laden."));
    }

    @Test
    void standortwechselMitOffenerReservationGibt409() throws Exception {
        long id = erfassen("BE 1007", "Biel Bahnhof");
        offeneReservation(id);

        putFahrzeug(id, "BE 1007", "Lyss Zentrum", 0)
                .andExpect(status().isConflict());
    }

    @Test
    void stammdatenAendernMitOffenerReservationIstErlaubt() throws Exception {
        long id = erfassen("BE 1008", "Biel Bahnhof");
        offeneReservation(id);

        putFahrzeug(id, "BE 1008", "Biel Bahnhof", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modell").value("Skoda Octavia"));
    }

    @Test
    void ausserBetriebNehmen() throws Exception {
        long id = erfassen("BE 1009", "Biel Bahnhof");

        mvc.perform(post("/api/fahrzeuge/{id}/ausser-betrieb", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inBetrieb").value(false));
    }

    @Test
    void ausserBetriebMitOffenerReservationGibt409() throws Exception {
        long id = erfassen("BE 1010", "Biel Bahnhof");
        offeneReservation(id);

        mvc.perform(post("/api/fahrzeuge/{id}/ausser-betrieb", id))
                .andExpect(status().isConflict());
    }

    @Test
    void listeNachStandortSortiertNachKennzeichen() throws Exception {
        erfassen("BE 1012", "Aarberg");
        erfassen("BE 1011", "Aarberg");
        erfassen("BE 1013", "Nidau");

        mvc.perform(get("/api/fahrzeuge").param("standortId", String.valueOf(standortId("Aarberg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anzahlGesamt").value(2))
                .andExpect(jsonPath("$.inhalt[0].kennzeichen").value("BE 1011"))
                .andExpect(jsonPath("$.inhalt[1].kennzeichen").value("BE 1012"))
                .andExpect(jsonPath("$.inhalt[0].standortName").value("Aarberg"));
    }

    private long erfassen(String kennzeichen, String standort) throws Exception {
        String antwort = postFahrzeug(kennzeichen, standortId(standort))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(antwort, "$.id")).longValue();
    }

    private ResultActions postFahrzeug(String kennzeichen, long standortId) throws Exception {
        String json = """
                {"kennzeichen": "%s", "modell": "VW Golf", "kategorie": "KOMBI",
                 "kmStand": 12000, "standortId": %d}
                """.formatted(kennzeichen, standortId);
        return mvc.perform(post("/api/fahrzeuge").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putFahrzeug(long id, String kennzeichen, String standort, long version) throws Exception {
        String json = """
                {"kennzeichen": "%s", "modell": "Skoda Octavia", "kategorie": "KOMBI",
                 "standortId": %d, "version": %d}
                """.formatted(kennzeichen, standortId(standort), version);
        return mvc.perform(put("/api/fahrzeuge/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long standortId(String name) {
        return standorte.findAll().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private void offeneReservation(long fahrzeugId) {
        Fahrzeug fahrzeug = fahrzeuge.findById(fahrzeugId).orElseThrow();
        Kunde kunde = kunden.save(new Kunde("Lea Gerber", "lea.gerber@example.ch"));
        reservationen.saveAndFlush(new Reservation(kunde, fahrzeug,
                OffsetDateTime.parse("2026-11-02T08:00:00+01:00"),
                OffsetDateTime.parse("2026-11-02T12:00:00+01:00")));
    }
}
