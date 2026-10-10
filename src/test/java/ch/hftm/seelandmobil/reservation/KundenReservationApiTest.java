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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A4 Reservationen eines Kunden: Filter, Sortierung und Seiten (T7).
 * 45 Reservationen, je zwei mit gleichem Beginn, jede fünfte storniert.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class KundenReservationApiTest {

    private static final OffsetDateTime START = OffsetDateTime.of(2040, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(1));

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

    private Kunde kunde;
    private final List<Reservation> erwartet = new ArrayList<>();

    @BeforeEach
    void testdaten() {
        Standort aarberg = standort("Aarberg");
        Standort ins = standort("Ins");
        Fahrzeug erstes = fahrzeuge.save(new Fahrzeug("BE 6001", "Fiat Panda", Kategorie.KLEIN, 100, aarberg));
        Fahrzeug zweites = fahrzeuge.save(new Fahrzeug("BE 6002", "Ford Transit", Kategorie.TRANSPORTER, 100, ins));
        kunde = kunden.save(new Kunde("Noah Keller", "noah.keller@example.ch"));
        Kunde andererKunde = kunden.save(new Kunde("Elena Frei", "elena.frei@example.ch"));

        for (int i = 0; i < 45; i++) {
            // Paare mit gleichem Beginn auf verschiedenen Fahrzeugen: Die Reihenfolge entscheidet die id.
            OffsetDateTime beginn = START.plusDays(i / 2);
            Fahrzeug fahrzeug = i % 2 == 0 ? erstes : zweites;
            Reservation reservation = new Reservation(kunde, fahrzeug, beginn, beginn.plusHours(2));
            if (i % 5 == 0) {
                reservation.stornieren();
            }
            erwartet.add(reservationen.save(reservation));
        }
        reservationen.save(new Reservation(andererKunde, erstes, START.plusDays(40), START.plusDays(40).plusHours(2)));
        reservationen.flush();
        erwartet.sort(Comparator.comparing(Reservation::getBeginn).thenComparing(Reservation::getId).reversed());
    }

    @Test
    void seitenEinsBisDreiSindLueckenlosOhneDuplikateUndStabil() throws Exception {
        List<Long> geliefert = new ArrayList<>();
        for (int seite = 0; seite < 3; seite++) {
            String json = mvc.perform(get("/api/kunden/{id}/reservationen", kunde.getId())
                            .param("seite", String.valueOf(seite))
                            .param("groesse", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.anzahlGesamt").value(45))
                    .andExpect(jsonPath("$.anzahlSeiten").value(3))
                    .andReturn().getResponse().getContentAsString();
            geliefert.addAll(ids(json));
        }

        assertThat(geliefert).containsExactlyElementsOf(erwartet.stream().map(Reservation::getId).toList());
    }

    @Test
    void zweiterAufrufLiefertDieselbeReihenfolge() throws Exception {
        String erster = mvc.perform(get("/api/kunden/{id}/reservationen", kunde.getId()).param("seite", "1"))
                .andReturn().getResponse().getContentAsString();
        String zweiter = mvc.perform(get("/api/kunden/{id}/reservationen", kunde.getId()).param("seite", "1"))
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(zweiter)).isEqualTo(ids(erster));
    }

    @Test
    void standardgroesseIst20() throws Exception {
        mvc.perform(get("/api/kunden/{id}/reservationen", kunde.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groesse").value(20))
                .andExpect(jsonPath("$.inhalt.length()").value(20));
    }

    @Test
    void jederEintragEnthaeltKennzeichenUndStandort() throws Exception {
        mvc.perform(get("/api/kunden/{id}/reservationen", kunde.getId()).param("groesse", "2"))
                .andExpect(jsonPath("$.inhalt[0].kennzeichen").value("BE 6001"))
                .andExpect(jsonPath("$.inhalt[0].standortName").value("Aarberg"))
                .andExpect(jsonPath("$.inhalt[1].kennzeichen").value("BE 6002"))
                .andExpect(jsonPath("$.inhalt[1].standortName").value("Ins"));
    }

    @Test
    void filterNachStatus() throws Exception {
        mvc.perform(get("/api/kunden/{id}/reservationen", kunde.getId()).param("status", "STORNIERT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anzahlGesamt").value(9))
                .andExpect(jsonPath("$.inhalt[*].status").value(everyItem(is("STORNIERT"))));
    }

    @Test
    void groesseUeber100Gibt400() throws Exception {
        mvc.perform(get("/api/kunden/{id}/reservationen", kunde.getId()).param("groesse", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fehler.groesse").exists());
    }

    @Test
    void unbekannterStatusGibt400() throws Exception {
        mvc.perform(get("/api/kunden/{id}/reservationen", kunde.getId()).param("status", "OFFEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fehler.status").value("ungültiger Wert 'OFFEN'"));
    }

    @Test
    void unbekannterKundeGibt404() throws Exception {
        mvc.perform(get("/api/kunden/{id}/reservationen", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void kundeOhneReservationenGibtLeereSeite() throws Exception {
        Kunde neu = kunden.save(new Kunde("Ohne Reservation", "ohne.reservation@example.ch"));

        mvc.perform(get("/api/kunden/{id}/reservationen", neu.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anzahlGesamt").value(0));
    }

    private Standort standort(String name) {
        return standorte.findAll().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static List<Long> ids(String json) {
        List<Number> ids = JsonPath.read(json, "$.inhalt[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}
