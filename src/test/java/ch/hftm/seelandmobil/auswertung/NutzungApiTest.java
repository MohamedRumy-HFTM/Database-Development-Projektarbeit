package ch.hftm.seelandmobil.auswertung;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A7 über die REST-API. Die Summen im Detail prüft {@link NutzungRepositoryTest}, den Cache
 * {@link NutzungCacheTest}. Der Cache wird hier vor und nach jedem Test geleert, weil die
 * Testdaten am Ende zurückgerollt werden.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class NutzungApiTest {

    private static final String VON = "2040-03-01T00:00:00Z";
    private static final String BIS = "2040-04-01T00:00:00Z";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CacheManager cacheManager;

    private long nidau;

    @BeforeEach
    void testdaten() {
        cacheLeeren();
        nidau = jdbc.queryForObject("select id from standort where name = 'Nidau'", Long.class);
        long kunde = jdbc.queryForObject(
                "insert into kunde (name, email) values ('Sara Graf', 'sara.graf@example.ch') returning id", Long.class);
        long fahrzeug = jdbc.queryForObject("""
                insert into fahrzeug (kennzeichen, modell, kategorie, standort_id)
                values ('BE 9301', 'Peugeot 208', 'KLEIN', ?) returning id
                """, Long.class, nidau);
        long reservation = jdbc.queryForObject("""
                insert into reservation (kunde_id, fahrzeug_id, standort_id, beginn, ende, status)
                values (?, ?, ?, '2040-03-12 08:00Z', '2040-03-12 20:00Z', 'ABGESCHLOSSEN') returning id
                """, Long.class, kunde, fahrzeug, nidau);
        jdbc.update("""
                insert into fahrt (reservation_id, km_start, km_ende, rueckgabe_am)
                values (?, 1000, 1150, '2040-03-12 15:26Z')
                """, reservation);
    }

    @AfterEach
    void cacheLeeren() {
        cacheManager.getCache(NutzungService.STANDORT_CACHE).clear();
    }

    @Test
    void jeStandortAlleAchtStandorte() throws Exception {
        mvc.perform(get("/api/auswertungen/standorte").param("von", VON).param("bis", BIS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].standortName").value("Nidau"))
                .andExpect(jsonPath("$[0].anzahlFahrten").value(1))
                .andExpect(jsonPath("$[0].nutzungsstunden").value(7.43))
                .andExpect(jsonPath("$[0].km").value(150));
    }

    @Test
    void jeFahrzeugMitAuslastung() throws Exception {
        mvc.perform(get("/api/auswertungen/fahrzeuge")
                        .param("von", VON).param("bis", BIS).param("standortId", String.valueOf(nidau)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kennzeichen").value("BE 9301"))
                .andExpect(jsonPath("$[0].auslastungProzent").value(1.00));   // 7.43 h ÷ 744 h
    }

    @Test
    void bisVorVonGibt400() throws Exception {
        mvc.perform(get("/api/auswertungen/standorte").param("von", BIS).param("bis", VON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("bis muss nach von liegen."));
    }

    @Test
    void fehlenderZeitraumGibt400() throws Exception {
        mvc.perform(get("/api/auswertungen/fahrzeuge").param("von", VON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fehler.bis").value("fehlt"));
    }
}
