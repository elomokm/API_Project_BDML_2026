package fr.efrei.messagerie.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.efrei.messagerie.TestcontainersConfiguration;
import fr.efrei.messagerie.entity.EtatMessage;
import fr.efrei.messagerie.entity.Message;
import fr.efrei.messagerie.entity.Utilisateur;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistance (étape 1, périmètre D-004), sur un vrai PostgreSQL.
 * @Transactional : chaque test est annulé à la fin, les tests ne se polluent pas entre eux.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PersistanceTests {

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private MessageRepository messages;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock horloge;

    @Test
    void unMessageEnregistrePuisRelu_conserveSesChampsEtEstEnvoye() {
        Utilisateur alice = utilisateurs.save(new Utilisateur("alice", "empreinte"));
        Utilisateur bob = utilisateurs.save(new Utilisateur("bob", "empreinte"));
        LocalDateTime maintenant = LocalDateTime.now(horloge).withNano(0);

        Long id = messages.save(new Message(alice, bob, "Bonjour !", maintenant)).getId();
        // Vide le cache JPA : la lecture suivante interroge réellement la base.
        entityManager.flush();
        entityManager.clear();

        Message relu = messages.findById(id).orElseThrow();
        assertThat(relu.getId()).isNotNull();
        assertThat(relu.getContenu()).isEqualTo("Bonjour !");
        assertThat(relu.getDateEnvoi()).isEqualTo(maintenant);
        assertThat(relu.getEtat()).isEqualTo(EtatMessage.ENVOYE);
        assertThat(relu.getExpediteur().getUsername()).isEqualTo("alice");
        assertThat(relu.getDestinataire().getUsername()).isEqualTo("bob");
    }

    @Test
    void unUsernameDejaPris_estRefuseParLaBase() {
        utilisateurs.saveAndFlush(new Utilisateur("alice", "empreinte"));

        // L'unicité est garantie par la contrainte UNIQUE de la base, pas par le code.
        assertThatThrownBy(() -> utilisateurs.saveAndFlush(new Utilisateur("alice", "autre")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laConversation_contientLesDeuxSens_etRienDautre() {
        Utilisateur alice = utilisateurs.save(new Utilisateur("alice", "empreinte"));
        Utilisateur bob = utilisateurs.save(new Utilisateur("bob", "empreinte"));
        Utilisateur charlie = utilisateurs.save(new Utilisateur("charlie", "empreinte"));
        LocalDateTime t = LocalDateTime.now(horloge).withNano(0);

        // Enregistrés volontairement dans le désordre des dates : le tri suit l'ordre d'arrivée
        // sur le serveur (id), qui est aussi le curseur de pagination (D-006).
        messages.save(new Message(bob, alice, "2. Salut Alice", t.plusMinutes(2)));
        messages.save(new Message(alice, bob, "1. Salut Bob", t.plusMinutes(1)));
        messages.save(new Message(alice, bob, "3. Ça va ?", t.plusMinutes(3)));
        // Deux autres conversations, qui ne doivent pas apparaître.
        messages.save(new Message(alice, charlie, "Hors sujet", t.plusMinutes(4)));
        messages.save(new Message(charlie, bob, "Hors sujet", t.plusMinutes(5)));
        entityManager.flush();
        entityManager.clear();

        // La requête lit du plus récent au plus ancien (id décroissant).
        List<String> attendus = List.of("3. Ça va ?", "1. Salut Bob", "2. Salut Alice");
        assertThat(messages.trouverDerniers(alice.getId(), bob.getId(), PageRequest.ofSize(10)))
                .extracting(Message::getContenu)
                .containsExactlyElementsOf(attendus);
        // Même conversation vue depuis Bob : l'ordre des paramètres ne change rien.
        assertThat(messages.trouverDerniers(bob.getId(), alice.getId(), PageRequest.ofSize(10)))
                .extracting(Message::getContenu)
                .containsExactlyElementsOf(attendus);
    }
}
