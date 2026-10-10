package ch.hftm.seelandmobil.fahrzeug;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
import ch.hftm.seelandmobil.kunde.Kunde;
import ch.hftm.seelandmobil.kunde.KundeRepository;
import ch.hftm.seelandmobil.reservation.Reservation;
import ch.hftm.seelandmobil.reservation.ReservationRepository;
import ch.hftm.seelandmobil.standort.Standort;
import ch.hftm.seelandmobil.standort.StandortRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A6 Verfügbare Fahrzeuge suchen. Am 5. März 2040 in Pieterlen:
 * <pre>
 * BE 8001  KLEIN  reserviert 10–14
 * BE 8002  KOMBI  frei
 * BE 8003  KLEIN  ausser Betrieb
 * BE 8004  KLEIN  Reservation 10–14 storniert
 * BE 8005  KLEIN  frei, aber in Büren a.A.
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class VerfuegbarkeitApiTest {

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

    private Standort pieterlen;

    @BeforeEach
    void testdaten() {
        pieterlen = standort("Pieterlen");
        Standort bueren = standort("Büren a.A.");
        Kunde kunde = kunden.save(new Kunde("Jonas Meier", "jonas.meier@example.ch"));

        Fahrzeug reserviert = fahrzeuge.save(new Fahrzeug("BE 8001", "Toyota Yaris", Kategorie.KLEIN, 0, pieterlen));
        fahrzeuge.save(new Fahrzeug("BE 8002", "Skoda Octavia", Kategorie.KOMBI, 0, pieterlen));
        Fahrzeug ausserBetrieb = new Fahrzeug("BE 8003", "Renault Zoe", Kategorie.KLEIN, 0, pieterlen);
        ausserBetrieb.ausserBetriebNehmen();
        fahrzeuge.save(ausserBetrieb);
        Fahrzeug storniert = fahrzeuge.save(new Fahrzeug("BE 8004", "Opel Corsa", Kategorie.KLEIN, 0, pieterlen));
        fahrzeuge.save(new Fahrzeug("BE 8005", "VW Up", Kategorie.KLEIN, 0, bueren));

        reservationen.save(new Reservation(kunde, reserviert, um(10), um(14)));
        Reservation storno = new Reservation(kunde, storniert, um(10), um(14));
        storno.stornieren();
        reservationen.save(storno);
        reservationen.flush();
    }

    @Test
    void ueberschneidendeReservationSchliesstAus() throws Exception {
        assertThat(kennzeichen(suche(13, 15).param("standortId", pieterlenId())))
                .containsExactly("BE 8002", "BE 8004");
    }

    @Test
    void direktAnschliessenderZeitraumIstFrei() throws Exception {
        assertThat(kennzeichen(suche(14, 16).param("standortId", pieterlenId())))
                .containsExactly("BE 8001", "BE 8002", "BE 8004");
        assertThat(kennzeichen(suche(8, 10).param("standortId", pieterlenId())))
                .contains("BE 8001");
    }

    @Test
    void ausserBetriebWirdNieGelistet() throws Exception {
        assertThat(kennzeichen(suche(18, 20))).doesNotContain("BE 8003");
    }

    @Test
    void ohneStandortfilterAlleStandorte() throws Exception {
        assertThat(kennzeichen(suche(13, 15)))
                .contains("BE 8002", "BE 8004", "BE 8005")
                .doesNotContain("BE 8001", "BE 8003");
    }

    @Test
    void filterNachKategorie() throws Exception {
        assertThat(kennzeichen(suche(13, 15).param("standortId", pieterlenId()).param("kategorie", "KLEIN")))
                .containsExactly("BE 8004");
    }

    @Test
    void seitenweiseNachKennzeichenSortiert() throws Exception {
        mvc.perform(suche(14, 16).param("standortId", pieterlenId()).param("groesse", "2"))
                .andExpect(jsonPath("$.anzahlGesamt").value(3))
                .andExpect(jsonPath("$.anzahlSeiten").value(2));

        assertThat(kennzeichen(suche(14, 16).param("standortId", pieterlenId()).param("groesse", "2")
                .param("seite", "0"))).containsExactly("BE 8001", "BE 8002");
        assertThat(kennzeichen(suche(14, 16).param("standortId", pieterlenId()).param("groesse", "2")
                .param("seite", "1"))).containsExactly("BE 8004");
    }

    @Test
    void ergebnisEnthaeltStandort() throws Exception {
        mvc.perform(suche(13, 15).param("standortId", pieterlenId()))
                .andExpect(jsonPath("$.inhalt[0].standortName").value("Pieterlen"))
                .andExpect(jsonPath("$.inhalt[0].kategorie").value("KOMBI"));
    }

    @Test
    void bisVorVonGibt400() throws Exception {
        mvc.perform(suche(15, 13))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("bis muss nach von liegen."));
    }

    @Test
    void fehlenderZeitraumGibt400() throws Exception {
        mvc.perform(get("/api/fahrzeuge/verfuegbar").param("bis", um(15).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fehler.von").value("fehlt"));
    }

    private MockHttpServletRequestBuilder suche(int von, int bis) {
        return get("/api/fahrzeuge/verfuegbar")
                .param("von", um(von).toString())
                .param("bis", um(bis).toString());
    }

    private List<String> kennzeichen(MockHttpServletRequestBuilder anfrage) throws Exception {
        String json = mvc.perform(anfrage)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.inhalt[*].kennzeichen");
    }

    private String pieterlenId() {
        return String.valueOf(pieterlen.getId());
    }

    private Standort standort(String name) {
        return standorte.findAll().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static OffsetDateTime um(int stunde) {
        return OffsetDateTime.of(2040, 3, 5, stunde, 0, 0, 0, ZoneOffset.ofHours(1));
    }
}
