package fr.efrei.messagerie.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;
import fr.efrei.messagerie.entity.EtatMessage;
import fr.efrei.messagerie.entity.Message;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Messages envoyés par le serveur sur le canal (D-012). Chacun porte un champ « type » qui
 * permet au client de l'aiguiller.
 */
public final class Evenements {

    private Evenements() {
    }

    /**
     * Un message de la conversation, au format imposé par l'énoncé (type, expediteurId,
     * destinataireId, contenu, dateEnvoi), complété par son identifiant (accusé et
     * déduplication, D-013) et son état de livraison.
     */
    public record EvenementMessage(
            String type,
            Long id,
            Long expediteurId,
            Long destinataireId,
            String contenu,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime dateEnvoi,
            EtatMessage etat) {

        public static EvenementMessage depuis(Message message) {
            return new EvenementMessage("message", message.getId(),
                    message.getExpediteur().getId(), message.getDestinataire().getId(),
                    message.getContenu(), message.getDateEnvoi(), message.getEtat());
        }
    }

    /** Un utilisateur vient de se connecter (première session) ou de se déconnecter (dernière). */
    public record EvenementPresence(String type, Long utilisateurId, boolean connecte) {

        public static EvenementPresence de(Long utilisateurId, boolean connecte) {
            return new EvenementPresence("presence", utilisateurId, connecte);
        }
    }

    /**
     * Changement d'état de messages envoyés par le destinataire de cet événement (D-017) :
     * ses coches passent à « livré » ou « lu ».
     */
    public record EvenementEtat(String type, List<Long> messageIds, EtatMessage etat) {

        public static EvenementEtat de(List<Long> messageIds, EtatMessage etat) {
            return new EvenementEtat("etat", messageIds, etat);
        }
    }

    /** Indicateur « en train d'écrire » (D-016, D-017), jamais enregistré. */
    public record EvenementSaisie(String type, Long expediteurId, boolean actif) {

        public static EvenementSaisie de(Long expediteurId, boolean actif) {
            return new EvenementSaisie("saisie", expediteurId, actif);
        }
    }

    /** Message refusé : code stable (D-012) et explication lisible. */
    public record EvenementErreur(String type, String code, String message) {

        public static EvenementErreur de(String code, String message) {
            return new EvenementErreur("erreur", code, message);
        }
    }
}
