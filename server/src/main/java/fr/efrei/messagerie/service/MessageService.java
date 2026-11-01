package fr.efrei.messagerie.service;

import fr.efrei.messagerie.entity.Message;
import fr.efrei.messagerie.exception.UtilisateurIntrouvableException;
import fr.efrei.messagerie.repository.MessageRepository;
import fr.efrei.messagerie.repository.UtilisateurRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historique des conversations (étape 3). L'envoi de messages arrive avec le WebSocket.
 */
@Service
public class MessageService {

    private final MessageRepository messages;
    private final UtilisateurRepository utilisateurs;

    public MessageService(MessageRepository messages, UtilisateurRepository utilisateurs) {
        this.messages = messages;
        this.utilisateurs = utilisateurs;
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
