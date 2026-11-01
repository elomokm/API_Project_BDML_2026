package fr.efrei.messagerie.service;

import fr.efrei.messagerie.entity.Message;
import fr.efrei.messagerie.entity.Utilisateur;
import fr.efrei.messagerie.exception.MessageInvalideException;
import fr.efrei.messagerie.exception.UtilisateurIntrouvableException;
import fr.efrei.messagerie.repository.MessageRepository;
import fr.efrei.messagerie.repository.UtilisateurRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envoi, livraison et historique des messages.
 */
@Service
public class MessageService {

    /** Taille maximale d'un message (D-012). */
    public static final int LONGUEUR_MAX_CONTENU = 2000;

    private final MessageRepository messages;
    private final UtilisateurRepository utilisateurs;
    private final Clock horloge;

    public MessageService(MessageRepository messages, UtilisateurRepository utilisateurs,
                          Clock horloge) {
        this.messages = messages;
        this.utilisateurs = utilisateurs;
        this.horloge = horloge;
    }

    /**
     * Valide puis enregistre un message (D-012). Étape (1) de l'énoncé : persister. La
     * retransmission, étape (2), est faite par l'appelant une fois ce message enregistré.
     *
     * Seul endroit du code qui fixe dateEnvoi (D-005) : toujours l'horloge du serveur, jamais
     * une valeur reçue du client. L'expéditeur est l'utilisateur authentifié au handshake.
     *
     * @throws MessageInvalideException si le message est refusé ; rien n'est alors enregistré
     */
    @Transactional
    public Message envoyer(Long expediteurId, Long destinataireId, String contenu) {
        if (Objects.equals(expediteurId, destinataireId)) {
            throw new MessageInvalideException("destinataire_soi_meme",
                    "Impossible de s'envoyer un message à soi-même.");
        }
        if (contenu == null || contenu.isBlank()) {
            throw new MessageInvalideException("contenu_vide", "Le message est vide.");
        }
        if (contenu.length() > LONGUEUR_MAX_CONTENU) {
            throw new MessageInvalideException("contenu_trop_long",
                    "Le message dépasse " + LONGUEUR_MAX_CONTENU + " caractères.");
        }
        Utilisateur destinataire = utilisateurs.findById(destinataireId)
                .orElseThrow(() -> new MessageInvalideException("destinataire_inexistant",
                        "Aucun utilisateur avec l'identifiant " + destinataireId + "."));
        // Référence sans requête SQL : l'expéditeur existe, son jeton vient d'être vérifié.
        Utilisateur expediteur = utilisateurs.getReferenceById(expediteurId);

        return messages.save(new Message(expediteur, destinataire, contenu,
                LocalDateTime.now(horloge)));
    }

    /**
     * Accusé automatique du client destinataire : le message passe à LIVRE (D-013).
     * Seul le destinataire réel peut accuser réception ; un accusé en double est sans effet.
     *
     * @throws MessageInvalideException si le message est inconnu ou destiné à un autre
     */
    @Transactional
    public void accuserReception(Long destinataireId, Long messageId) {
        Message message = messages.findById(messageId)
                .filter(m -> m.getDestinataire().getId().equals(destinataireId))
                .orElseThrow(() -> new MessageInvalideException("message_invalide",
                        "Aucun message " + messageId + " ne vous est destiné."));
        // Modification détectée par JPA et écrite en base à la fin de la transaction.
        message.marquerLivre();
    }

    /** Messages en attente de livraison pour un destinataire, à rattraper à sa connexion. */
    @Transactional(readOnly = true)
    public List<Message> nonLivres(Long destinataireId) {
        return messages.trouverNonLivres(destinataireId);
    }

    /**
     * Conversation entre l'utilisateur connecté et un autre utilisateur, paginée par curseur
     * (D-006), du plus ancien au plus récent.
     *
     * « moi » vient toujours du jeton, jamais d'un paramètre : impossible de lire la
     * conversation de deux autres personnes, l'utilisateur fait toujours partie de la paire.
     *
     * @param avant  identifiant du message curseur ; null pour les messages les plus récents
     * @param limite nombre maximal de messages
     * @throws UtilisateurIntrouvableException si l'interlocuteur n'existe pas (404)
     */
    @Transactional(readOnly = true)
    public List<Message> historique(Long moi, Long autre, Long avant, int limite) {
        if (!utilisateurs.existsById(autre)) {
            throw new UtilisateurIntrouvableException(autre);
        }
        PageRequest page = PageRequest.ofSize(limite);
        List<Message> plusRecentsDabord = avant == null
                ? messages.trouverDerniers(moi, autre, page)
                : messages.trouverAvant(moi, autre, avant, page);

        // La requête lit du plus récent au plus ancien (pour garder les N derniers) ;
        // l'historique s'affiche du plus ancien au plus récent.
        List<Message> chronologique = new ArrayList<>(plusRecentsDabord);
        Collections.reverse(chronologique);
        return chronologique;
    }
}
