package fr.efrei.messagerie.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import fr.efrei.messagerie.TestcontainersConfiguration;
import fr.efrei.messagerie.entity.EtatMessage;
import fr.efrei.messagerie.entity.Utilisateur;
import fr.efrei.messagerie.repository.MessageRepository;
import fr.efrei.messagerie.repository.UtilisateurRepository;
import fr.efrei.messagerie.security.JwtService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Canal temps réel (étape 4, D-009 à D-013), de bout en bout : un vrai serveur sur un port
 * aléatoire, de vrais clients WebSocket, une vraie base PostgreSQL.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CanalWebSocketTests {

    private static final long ATTENTE_S = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private MessageRepository messages;

    @Autowired
    private JwtService jwtService;

    private Utilisateur alice;
    private Utilisateur bob;
    private final List<Client> clients = new ArrayList<>();

    @BeforeEach
    void creerUtilisateurs() {
        // Noms uniques : les tests partagent la même base, sans retour arrière (vrai serveur).
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        alice = utilisateurs.save(new Utilisateur("alice_" + suffixe, "empreinte"));
        bob = utilisateurs.save(new Utilisateur("bob_" + suffixe, "empreinte"));
    }

    @AfterEach
    void fermerClients() throws Exception {
        for (Client client : clients) {
            client.fermer();
        }
    }

    // 4a. Ouverture du canal

    @Test
    void sansJeton_leHandshakeEstRefuse() {
        assertThatThrownBy(() -> connecter(null)).isNotNull();
    }

    @Test
    void avecUnJetonInvalide_leHandshakeEstRefuse() {
        assertThatThrownBy(() -> connecter("pas.un.jeton")).isNotNull();
    }

    @Test
    void avecUnJetonValide_leCanalSOuvre_avecLeSousProtocoleSeulement() throws Exception {
        Client client = connecter(jwtService.generer(alice));

        assertThat(client.session.isOpen()).isTrue();
        // Le serveur ne renvoie que « messagerie.v1 », jamais le jeton.
        assertThat(client.session.getAcceptedProtocol()).isEqualTo("messagerie.v1");
    }

    // 4b et 4c. Routage, persistance, retransmission

    @Test
    void unMessageDeAliceArriveInstantanementChezBob_auFormatDeLEnonce() throws Exception {
        Client a = connecter(jwtService.generer(alice));
        Client b = connecter(jwtService.generer(bob));

        a.envoyer("""
                {"type":"message","destinataireId":%d,"contenu":"Bonjour !"}""".formatted(bob.getId()));

        DocumentContext recu = b.attendre(type("message"));
        assertThat(recu.<String>read("$.type")).isEqualTo("message");
        assertThat(recu.<Number>read("$.expediteurId").longValue()).isEqualTo(alice.getId());
        assertThat(recu.<Number>read("$.destinataireId").longValue()).isEqualTo(bob.getId());
        assertThat(recu.<String>read("$.contenu")).isEqualTo("Bonjour !");
        assertThat(recu.<String>read("$.dateEnvoi")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");

        // (1) Persisté avant d'être retransmis.
        long id = recu.<Number>read("$.id").longValue();
        assertThat(messages.findById(id)).isPresent();
        // L'expéditeur reçoit l'écho du message enregistré.
        assertThat(a.attendre(type("message")).<Number>read("$.id").longValue()).isEqualTo(id);
    }

    @Test
    void unExpediteurUsurpe_estRefuse_etRienNEstEnregistre() throws Exception {
        Client b = connecter(jwtService.generer(bob));
        long avant = messages.count();

        // Connecté en tant que bob, il prétend être alice.
        b.envoyer("""
                {"type":"message","expediteurId":%d,"destinataireId":%d,"contenu":"Usurpation"}"""
                .formatted(alice.getId(), alice.getId()));

        assertThat(b.attendre(type("erreur")).<String>read("$.code")).isEqualTo("expediteur_invalide");
        assertThat(messages.count()).isEqualTo(avant);
    }

    @Test
    void uneDateEnvoyeeParLeClient_estIgnoree() throws Exception {
        Client a = connecter(jwtService.generer(alice));

        a.envoyer("""
                {"type":"message","destinataireId":%d,"contenu":"Antidaté","dateEnvoi":"2000-01-01T00:00:00"}"""
                .formatted(bob.getId()));

        assertThat(a.attendre(type("message")).<String>read("$.dateEnvoi")).doesNotStartWith("2000");
    }

    @Test
    void lesMessagesInvalides_produisentUneErreur_sansFermerLaConnexion() throws Exception {
        Client a = connecter(jwtService.generer(alice));

        a.envoyer("pas du JSON");
        assertThat(a.attendre(type("erreur")).<String>read("$.code")).isEqualTo("json_invalide");
        a.envoyer("{\"type\":\"inconnu\"}");
        assertThat(a.attendre(type("erreur")).<String>read("$.code")).isEqualTo("type_inconnu");
        a.envoyer("{\"type\":\"message\",\"destinataireId\":%d,\"contenu\":\"   \"}".formatted(bob.getId()));
        assertThat(a.attendre(type("erreur")).<String>read("$.code")).isEqualTo("contenu_vide");
        a.envoyer("{\"type\":\"message\",\"destinataireId\":%d,\"contenu\":\"%s\"}"
                .formatted(bob.getId(), "x".repeat(2001)));
        assertThat(a.attendre(type("erreur")).<String>read("$.code")).isEqualTo("contenu_trop_long");
        a.envoyer("{\"type\":\"message\",\"destinataireId\":999999,\"contenu\":\"Allô ?\"}");
        assertThat(a.attendre(type("erreur")).<String>read("$.code")).isEqualTo("destinataire_inexistant");
        a.envoyer("{\"type\":\"message\",\"destinataireId\":%d,\"contenu\":\"Moi\"}".formatted(alice.getId()));
        assertThat(a.attendre(type("erreur")).<String>read("$.code")).isEqualTo("destinataire_soi_meme");

        assertThat(a.session.isOpen()).isTrue();
    }

    @Test
    void uneTrameDemesuree_fermeLaConnexion_avantMemeDEtreLue() throws Exception {
        Client a = connecter(jwtService.generer(alice));

        a.envoyer("x".repeat(20 * 1024)); // au-delà de la limite de 16 Kio

        attendreQue(() -> !a.session.isOpen());
    }

    @Test
    void unUtilisateurSurDeuxAppareils_recoitLeMessageSurLesDeux() throws Exception {
        Client a = connecter(jwtService.generer(alice));
        Client bTelephone = connecter(jwtService.generer(bob));
        Client bOrdinateur = connecter(jwtService.generer(bob));

        a.envoyer("{\"type\":\"message\",\"destinataireId\":%d,\"contenu\":\"Partout\"}".formatted(bob.getId()));

        assertThat(bTelephone.attendre(type("message")).<String>read("$.contenu")).isEqualTo("Partout");
        assertThat(bOrdinateur.attendre(type("message")).<String>read("$.contenu")).isEqualTo("Partout");
    }

    // 4d. Présence

    @Test
    void laPresence_suitLesConnexionsReelles_enRestEtEnDirect() throws Exception {
        Client a = connecter(jwtService.generer(alice));
        assertThat(statutDe(bob)).isFalse();

        Client b = connecter(jwtService.generer(bob));
        DocumentContext arrivee = a.attendre(presenceDe(bob));
        assertThat(arrivee.<Boolean>read("$.connecte")).isTrue();
        assertThat(statutDe(bob)).isTrue();

        b.fermer();
        DocumentContext depart = a.attendre(presenceDe(bob));
        assertThat(depart.<Boolean>read("$.connecte")).isFalse();
        assertThat(statutDe(bob)).isFalse();
    }

    // 4e. Destinataire hors ligne (D-013)

    @Test
    void unMessagePourUnAbsent_luiEstPousseAsaConnexion_puisLivreApresSonAccuse() throws Exception {
        Client a = connecter(jwtService.generer(alice));
        a.envoyer("{\"type\":\"message\",\"destinataireId\":%d,\"contenu\":\"Tu es là ?\"}".formatted(bob.getId()));
        long id = a.attendre(type("message")).<Number>read("$.id").longValue();
        assertThat(etatDe(id)).isEqualTo(EtatMessage.ENVOYE);

        // Bob se connecte plus tard : le message lui est poussé sans qu'il le demande.
        Client b = connecter(jwtService.generer(bob));
        DocumentContext rattrape = b.attendre(type("message"));
        assertThat(rattrape.<Number>read("$.id").longValue()).isEqualTo(id);
        assertThat(rattrape.<String>read("$.etat")).isEqualTo("ENVOYE");

        // Accusé automatique du client : le message devient LIVRE.
        b.envoyer("{\"type\":\"accuse\",\"messageId\":%d}".formatted(id));
        attendreQue(() -> etatDe(id) == EtatMessage.LIVRE);

        // À la reconnexion suivante, plus rien à rattraper.
        b.fermer();
        Client bDeRetour = connecter(jwtService.generer(bob));
        assertThat(bDeRetour.attendreRien(type("message"))).isTrue();
    }

    @Test
    void seulLeDestinataire_peutAccuserReception() throws Exception {
        Client a = connecter(jwtService.generer(alice));
        a.envoyer("{\"type\":\"message\",\"destinataireId\":%d,\"contenu\":\"Pour Bob\"}".formatted(bob.getId()));
        long id = a.attendre(type("message")).<Number>read("$.id").longValue();

        // Alice tente d'accuser réception à la place de Bob.
        a.envoyer("{\"type\":\"accuse\",\"messageId\":%d}".formatted(id));

        assertThat(a.attendre(type("erreur")).<String>read("$.code")).isEqualTo("message_invalide");
        assertThat(etatDe(id)).isEqualTo(EtatMessage.ENVOYE);
    }

    // Outils de test

    private Client connecter(String jeton) throws Exception {
        WebSocketHttpHeaders entetes = new WebSocketHttpHeaders();
        entetes.setSecWebSocketProtocol(jeton == null
                ? List.of("messagerie.v1")
                : List.of("messagerie.v1", jeton));
        Client client = new Client();
        client.session = new StandardWebSocketClient()
                .execute(client, entetes, URI.create("ws://localhost:" + port + "/ws/messages"))
                .get(ATTENTE_S, TimeUnit.SECONDS);
        clients.add(client);
        return client;
    }

    private boolean statutDe(Utilisateur utilisateur) throws Exception {
        HttpResponse<String> reponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/utilisateurs/" + utilisateur.getId()))
                        .header("Authorization", "Bearer " + jwtService.generer(alice))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return JsonPath.read(reponse.body(), "$.connecte");
    }

    private EtatMessage etatDe(long messageId) {
        return messages.findById(messageId).orElseThrow().getEtat();
    }

    private static void attendreQue(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long limite = System.currentTimeMillis() + ATTENTE_S * 1000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("condition non atteinte à temps").isLessThan(limite);
            Thread.sleep(50);
        }
    }

    private static Predicate<DocumentContext> type(String type) {
        return evenement -> type.equals(evenement.read("$.type"));
    }

    private static Predicate<DocumentContext> presenceDe(Utilisateur utilisateur) {
        return evenement -> "presence".equals(evenement.read("$.type"))
                && evenement.<Number>read("$.utilisateurId").longValue() == utilisateur.getId();
    }

    /** Client WebSocket de test : range chaque message reçu dans une file. */
    private static class Client extends TextWebSocketHandler {

        private final BlockingQueue<String> recus = new LinkedBlockingQueue<>();
        private WebSocketSession session;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            recus.add(message.getPayload());
        }

        void envoyer(String json) throws Exception {
            session.sendMessage(new TextMessage(json));
        }

        /** Attend le prochain message reçu qui satisfait le filtre (les autres sont ignorés). */
        DocumentContext attendre(Predicate<DocumentContext> filtre) throws InterruptedException {
            long limite = System.currentTimeMillis() + ATTENTE_S * 1000;
            while (System.currentTimeMillis() < limite) {
                String recu = recus.poll(100, TimeUnit.MILLISECONDS);
                if (recu != null) {
                    DocumentContext evenement = JsonPath.parse(recu);
                    if (filtre.test(evenement)) {
                        return evenement;
                    }
                }
            }
            throw new AssertionError("Aucun message attendu reçu en " + ATTENTE_S + " s");
        }

        /** Vérifie qu'aucun message de ce genre n'arrive pendant une seconde. */
        boolean attendreRien(Predicate<DocumentContext> filtre) throws InterruptedException {
            long limite = System.currentTimeMillis() + 1000;
            while (System.currentTimeMillis() < limite) {
                String recu = recus.poll(100, TimeUnit.MILLISECONDS);
                if (recu != null && filtre.test(JsonPath.parse(recu))) {
                    return false;
                }
            }
            return true;
        }

        void fermer() throws Exception {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }
}
