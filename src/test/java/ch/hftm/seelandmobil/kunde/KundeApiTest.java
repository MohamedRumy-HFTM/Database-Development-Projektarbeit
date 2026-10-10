package ch.hftm.seelandmobil.kunde;

import ch.hftm.seelandmobil.TestcontainersConfiguration;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A2 Kunden verwalten: Erfassen, Lesen, Ändern und Sperren über die REST-API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class KundeApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void erfasstKundeUndLiefertIhnAus() throws Exception {
        long id = erfassen("Lea Gerber", "lea.gerber@example.ch");

        mvc.perform(get("/api/kunden/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lea Gerber"))
                .andExpect(jsonPath("$.email").value("lea.gerber@example.ch"))
                .andExpect(jsonPath("$.gesperrt").value(false));
    }

    @Test
    void emailIstUnabhaengigVonGrossKleinschreibungEindeutig() throws Exception {
        erfassen("Lea Gerber", "lea.gerber@example.ch");

        postKunde("Lea Gerber", "Lea.Gerber@Example.ch")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ein Kunde mit dieser E-Mail-Adresse existiert bereits."));
    }

    @Test
    void ungueltigeEmailGibt400() throws Exception {
        postKunde("Nico Weber", "nico.weber")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fehler.email").exists());
    }

    @Test
    void leererNameGibt400() throws Exception {
        postKunde(" ", "nico.weber@example.ch")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fehler.name").value("darf nicht leer sein"));
    }

    @Test
    void unbekannterKundeGibt404() throws Exception {
        mvc.perform(get("/api/kunden/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Kunde 999999 existiert nicht."));
    }

    @Test
    void aendernUebernimmtNameUndEmail() throws Exception {
        long id = erfassen("Lea Gerber", "lea.gerber@example.ch");

        putKunde(id, "Lea Schneider", "lea.schneider@example.ch")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lea Schneider"))
                .andExpect(jsonPath("$.email").value("lea.schneider@example.ch"));
    }

    @Test
    void aendernAufVergebeneEmailGibt409() throws Exception {
        erfassen("Lea Gerber", "lea.gerber@example.ch");
        long id = erfassen("Nico Weber", "nico.weber@example.ch");

        putKunde(id, "Nico Weber", "LEA.GERBER@example.ch")
                .andExpect(status().isConflict());
    }

    @Test
    void sperren() throws Exception {
        long id = erfassen("Lea Gerber", "lea.gerber@example.ch");

        mvc.perform(post("/api/kunden/{id}/sperren", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gesperrt").value(true));
    }

    @Test
    void sucheInNameUndEmailOhneGrossKleinschreibung() throws Exception {
        erfassen("Lea Gerber", "lea.gerber@example.ch");
        erfassen("Nico Weber", "nico.weber@example.ch");

        mvc.perform(get("/api/kunden").param("suche", "GERBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anzahlGesamt").value(1))
                .andExpect(jsonPath("$.inhalt[0].name").value("Lea Gerber"));
        mvc.perform(get("/api/kunden").param("suche", "weber@"))
                .andExpect(jsonPath("$.anzahlGesamt").value(1))
                .andExpect(jsonPath("$.inhalt[0].name").value("Nico Weber"));
    }

    @Test
    void platzhalterInDerSucheGeltenWoertlich() throws Exception {
        erfassen("Lea Gerber", "lea.gerber@example.ch");

        mvc.perform(get("/api/kunden").param("suche", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anzahlGesamt").value(0));
        mvc.perform(get("/api/kunden").param("suche", "lea_gerber"))
                .andExpect(jsonPath("$.anzahlGesamt").value(0));
    }

    private long erfassen(String name, String email) throws Exception {
        String antwort = postKunde(name, email)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(antwort, "$.id")).longValue();
    }

    private ResultActions postKunde(String name, String email) throws Exception {
        return mvc.perform(post("/api/kunden").contentType(MediaType.APPLICATION_JSON).content(json(name, email)));
    }

    private ResultActions putKunde(long id, String name, String email) throws Exception {
        return mvc.perform(put("/api/kunden/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json(name, email)));
    }

    private static String json(String name, String email) {
        return """
                {"name": "%s", "email": "%s"}
                """.formatted(name, email);
    }
}
