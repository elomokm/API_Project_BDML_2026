package fr.efrei.messagerie.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.efrei.messagerie.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * CORS (D-015) : les pages des clients (port 8081, en local ou sur le réseau privé) peuvent
 * appeler l'API ; une page d'une autre origine ne le peut pas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CorsApiTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void laVerificationPrealable_duNavigateur_estAccepteePourUnClientDuReseauLocal() throws Exception {
        mvc.perform(options("/api/utilisateurs")
                        .header("Origin", "http://192.168.1.44:8081")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.1.44:8081"));
    }

    @Test
    void uneOrigineInconnue_estRefusee() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "https://site-malveillant.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unAutrePortQueCeluiDesClients_estRefuse() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lEnteteLocation_estLisibleParLeClient() throws Exception {
        mvc.perform(post("/api/comptes")
                        .header("Origin", "http://localhost:8081")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cors_test\",\"motDePasse\":\"motdepasse123\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Expose-Headers", "Location"));
    }
}
