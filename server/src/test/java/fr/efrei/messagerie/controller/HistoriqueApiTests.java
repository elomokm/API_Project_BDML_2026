package fr.efrei.messagerie.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.efrei.messagerie.TestcontainersConfiguration;
import fr.efrei.messagerie.entity.Message;
import fr.efrei.messagerie.entity.Utilisateur;
import fr.efrei.messagerie.repository.MessageRepository;
import fr.efrei.messagerie.repository.UtilisateurRepository;
import fr.efrei.messagerie.security.JwtService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/messages/{utilisateurId} (étape 3, D-006) : conversation de l'utilisateur du jeton,
 * dans les deux sens, paginée par curseur.
 * Les messages sont insérés directement en base : leur envoi arrive avec le WebSocket.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HistoriqueApiTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private MessageRepository messages;

    @Autowired
    private JwtService jwtService;

    private Utilisateur alice;
    private Utilisateur bob;
    private Utilisateur charlie;
    private final List<Long> idsAliceBob = new ArrayList<>();

    @BeforeEach
    void preparerConversation() {
        alice = utilisateurs.save(new Utilisateur("alice", "empreinte"));
        bob = utilisateurs.save(new Utilisateur("bob", "empreinte"));
        charlie = utilisateurs.save(new Utilisateur("charlie", "empreinte"));
        LocalDateTime t = LocalDateTime.of(2026, 9, 21, 10, 15, 30);

        // Cinq messages entre alice et bob, en alternant les sens.
        for (int i = 1; i <= 5; i++) {
            Utilisateur de = i % 2 == 1 ? alice : bob;
            Utilisateur vers = i % 2 == 1 ? bob : alice;
            idsAliceBob.add(messages.save(new Message(de, vers, "m" + i, t.plusMinutes(i))).getId());
        }
        // Une autre conversation, qui ne doit jamais apparaître.
        messages.save(new Message(charlie, bob, "privé", t.plusMinutes(10)));
    }

    private String jetonDe(Utilisateur utilisateur) {
        return "Bearer " + jwtService.generer(utilisateur);
    }

    @Test
    void laConversation_contientLesDeuxSens_dansLOrdreChronologique_etRienDautre() throws Exception {
        mvc.perform(get("/api/messages/" + bob.getId()).header("Authorization", jetonDe(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].contenu", contains("m1", "m2", "m3", "m4", "m5")));
    }

    @Test
    void laMemeConversation_vueDepuisLAutre_estIdentique() throws Exception {
        mvc.perform(get("/api/messages/" + alice.getId()).header("Authorization", jetonDe(bob)))
                .andExpect(jsonPath("$[*].contenu", contains("m1", "m2", "m3", "m4", "m5")));
    }

    @Test
    void unMessage_respecteLeFormatDeLEnonce() throws Exception {
        mvc.perform(get("/api/messages/" + bob.getId()).header("Authorization", jetonDe(alice)))
                .andExpect(jsonPath("$[0].id").value(idsAliceBob.get(0)))
                .andExpect(jsonPath("$[0].expediteurId").value(alice.getId()))
                .andExpect(jsonPath("$[0].destinataireId").value(bob.getId()))
                .andExpect(jsonPath("$[0].contenu").value("m1"))
                .andExpect(jsonPath("$[0].dateEnvoi").value("2026-09-21T10:16:30"))
                .andExpect(jsonPath("$[0].etat").value("ENVOYE"));
    }

    @Test
    void sansAvant_renvoieLesPlusRecents_avecAvant_remonteLaConversation() throws Exception {
        String jeton = jetonDe(alice);
        // Les 2 plus récents.
        mvc.perform(get("/api/messages/" + bob.getId()).param("limite", "2").header("Authorization", jeton))
                .andExpect(jsonPath("$[*].contenu", contains("m4", "m5")));
        // Les 2 qui précèdent m4 (le curseur).
        mvc.perform(get("/api/messages/" + bob.getId())
                        .param("avant", String.valueOf(idsAliceBob.get(3)))
                        .param("limite", "2")
                        .header("Authorization", jeton))
                .andExpect(jsonPath("$[*].contenu", contains("m2", "m3")));
        // Au début de la conversation : il ne reste que m1.
        mvc.perform(get("/api/messages/" + bob.getId())
                        .param("avant", String.valueOf(idsAliceBob.get(1)))
                        .param("limite", "2")
                        .header("Authorization", jeton))
                .andExpect(jsonPath("$[*].contenu", contains("m1")));
    }

    @Test
    void unInterlocuteurInexistant_404() throws Exception {
        mvc.perform(get("/api/messages/999999").header("Authorization", jetonDe(alice)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void uneLimiteHorsBornes_400() throws Exception {
        mvc.perform(get("/api/messages/" + bob.getId()).param("limite", "101").header("Authorization", jetonDe(alice)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void charlie_neVoitQueSaPropreConversationAvecBob() throws Exception {
        // « moi » vient du jeton : charlie ne peut pas lire la conversation alice et bob.
        mvc.perform(get("/api/messages/" + bob.getId()).header("Authorization", jetonDe(charlie)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].contenu").value("privé"));
    }

    @Test
    void sansJeton_401() throws Exception {
        mvc.perform(get("/api/messages/" + bob.getId())).andExpect(status().isUnauthorized());
    }
}
