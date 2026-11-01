package fr.efrei.messagerie.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.efrei.messagerie.TestcontainersConfiguration;
import fr.efrei.messagerie.entity.Utilisateur;
import fr.efrei.messagerie.repository.UtilisateurRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Protection par JWT (D-004, D-008) : les quatre cas imposés (sans jeton, expiré, altéré,
 * valide), plus deux attaques classiques (charge modifiée, jeton non signé).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SecuriteApiTests {

    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64URL_DECODE = Base64.getUrlDecoder();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Value("${app.jwt.secret}")
    private String secret;

    private Utilisateur alice;
    private Utilisateur bob;

    @BeforeEach
    void creerUtilisateurs() {
        alice = utilisateurs.save(new Utilisateur("alice", "empreinte"));
        bob = utilisateurs.save(new Utilisateur("bob", "empreinte"));
    }

    private ResultActions listerAvec(String enteteAuthorization) throws Exception {
        var requete = get("/api/utilisateurs");
        if (enteteAuthorization != null) {
            requete.header("Authorization", enteteAuthorization);
        }
        return mvc.perform(requete);
    }

    private static void verifierRefus401(ResultActions resultat) throws Exception {
        resultat.andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void sansJeton_401() throws Exception {
        verifierRefus401(listerAvec(null));
    }

    @Test
    void jetonExpire_401() throws Exception {
        // Jeton émis il y a deux heures, valable une heure : expiré depuis une heure.
        Clock ilYaDeuxHeures = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String expire = new JwtService(secret, Duration.ofHours(1), ilYaDeuxHeures).generer(alice);

        verifierRefus401(listerAvec("Bearer " + expire));
    }

    @Test
    void signatureAlteree_401() throws Exception {
        String[] parties = jwtService.generer(alice).split("\\.");
        char[] signature = parties[2].toCharArray();
        // Un caractère au milieu de la signature (le dernier ne porte parfois que des bits
        // de remplissage du Base64, le modifier ne changerait rien).
        int milieu = signature.length / 2;
        signature[milieu] = signature[milieu] == 'A' ? 'B' : 'A';
        String altere = parties[0] + "." + parties[1] + "." + new String(signature);

        verifierRefus401(listerAvec("Bearer " + altere));
    }

    @Test
    void jetonValide_200() throws Exception {
        listerAvec("Bearer " + jwtService.generer(alice)).andExpect(status().isOk());
    }

    @Test
    void chargeModifieePourSeFairePasserPourUnAutre_401() throws Exception {
        // L'attaquant, connecté en alice, remplace son identifiant par celui de bob dans la
        // charge, sans pouvoir recalculer la signature (il n'a pas le secret).
        String[] parties = jwtService.generer(alice).split("\\.");
        String charge = new String(BASE64URL_DECODE.decode(parties[1]), StandardCharsets.UTF_8);
        String chargeFalsifiee = charge.replace(
                "\"sub\":\"" + alice.getId() + "\"", "\"sub\":\"" + bob.getId() + "\"");
        String falsifie = parties[0] + "."
                + BASE64URL.encodeToString(chargeFalsifiee.getBytes(StandardCharsets.UTF_8)) + "."
                + parties[2];

        verifierRefus401(listerAvec("Bearer " + falsifie));
    }

    @Test
    void jetonNonSigne_algNone_401() throws Exception {
        // Attaque historique : déclarer « alg: none » et supprimer la signature.
        String entete = BASE64URL.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String charge = BASE64URL.encodeToString(
                ("{\"sub\":\"" + alice.getId() + "\",\"username\":\"alice\"}").getBytes(StandardCharsets.UTF_8));

        verifierRefus401(listerAvec("Bearer " + entete + "." + charge + "."));
    }

    @Test
    void schemaAutreQueBearer_401() throws Exception {
        verifierRefus401(listerAvec("Basic " + jwtService.generer(alice)));
    }
}
