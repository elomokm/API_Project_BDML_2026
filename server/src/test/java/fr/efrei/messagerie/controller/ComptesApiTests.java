package fr.efrei.messagerie.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import fr.efrei.messagerie.TestcontainersConfiguration;
import fr.efrei.messagerie.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * API des comptes (étape 2, périmètre D-004 et D-006), de la requête HTTP jusqu'à PostgreSQL.
 * MockMvc simule les requêtes HTTP sans ouvrir de port réseau.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ComptesApiTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UtilisateurRepository utilisateurs;

    private ResultActions creerCompte(String username, String motDePasse) throws Exception {
        return mvc.perform(post("/api/comptes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "motDePasse": "%s"}
                        """.formatted(username, motDePasse)));
    }

    private ResultActions connecter(String username, String motDePasse) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "motDePasse": "%s"}
                        """.formatted(username, motDePasse)));
    }

    // Création de compte

    @Test
    void creerUnCompte_renvoie201_avecLocation_etSansMotDePasse() throws Exception {
        creerCompte("alice", "motdepasse123")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/utilisateurs/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.motDePasse").doesNotExist());
    }

    @Test
    void leMotDePasse_estStockeHacheAvecArgon2id_jamaisEnClair() throws Exception {
        creerCompte("alice", "motdepasse123").andExpect(status().isCreated());

        String stocke = utilisateurs.findByUsername("alice").orElseThrow().getMotDePasse();
        assertThat(stocke).startsWith("$argon2id$").doesNotContain("motdepasse123");
    }

    @Test
    void unUsernameDejaPris_renvoie409_auFormatProblemDetail() throws Exception {
        creerCompte("alice", "motdepasse123").andExpect(status().isCreated());

        creerCompte("alice", "autremotdepasse")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Username déjà pris"));
    }

    @Test
    void unCorpsInvalide_renvoie400_avecLaListeDesChampsFautifs() throws Exception {
        creerCompte("a b", "court")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.erreurs[*].champ", hasItem("username")))
                .andExpect(jsonPath("$.erreurs[*].champ", hasItem("motDePasse")));
    }

    @Test
    void unJsonMalforme_renvoie400() throws Exception {
        mvc.perform(post("/api/comptes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ ceci n'est pas du JSON"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // Connexion

    @Test
    void seConnecterAvecLesBonsIdentifiants_renvoie200_etUnJeton() throws Exception {
        creerCompte("alice", "motdepasse123").andExpect(status().isCreated());

        connecter("alice", "motdepasse123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jeton").isString())
                .andExpect(jsonPath("$.typeJeton").value("Bearer"))
                .andExpect(jsonPath("$.expireDans").value(3600))
                .andExpect(jsonPath("$.utilisateur.username").value("alice"))
                .andExpect(jsonPath("$..motDePasse").isEmpty());
    }

    @Test
    void mauvaisMotDePasse_etCompteInexistant_donnentExactementLaMemeReponse() throws Exception {
        creerCompte("alice", "motdepasse123").andExpect(status().isCreated());

        String mauvaisMotDePasse = connecter("alice", "mauvaismotdepasse")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String compteInexistant = connecter("inconnu", "mauvaismotdepasse")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Rien dans la réponse ne permet de savoir si « alice » existe.
        assertThat(compteInexistant).isEqualTo(mauvaisMotDePasse);
    }

    // Utilisateurs

    /** Se connecte et renvoie l'en-tête « Authorization: Bearer <jeton> » à joindre. */
    private String entete(String username, String motDePasse) throws Exception {
        String reponse = connecter(username, motDePasse).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(reponse, "$.jeton");
    }

    @Test
    void laListeDesUtilisateurs_neContientJamaisDeMotDePasse() throws Exception {
        creerCompte("bob", "motdepasse123").andExpect(status().isCreated());
        creerCompte("alice", "motdepasse123").andExpect(status().isCreated());

        mvc.perform(get("/api/utilisateurs").header("Authorization", entete("alice", "motdepasse123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[1].username").value("bob"))
                .andExpect(jsonPath("$[*].motDePasse").isEmpty());
    }

    @Test
    void lAdresseRenvoyeeDansLocation_existe_etUnIdentifiantInconnuDonne404() throws Exception {
        String location = creerCompte("alice", "motdepasse123")
                .andReturn().getResponse().getHeader("Location");

        String jeton = entete("alice", "motdepasse123");
        mvc.perform(get(location).header("Authorization", jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
        mvc.perform(get("/api/utilisateurs/999999").header("Authorization", jeton))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
