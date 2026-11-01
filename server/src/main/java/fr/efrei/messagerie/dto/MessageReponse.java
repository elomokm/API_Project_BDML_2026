package fr.efrei.messagerie.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import fr.efrei.messagerie.entity.EtatMessage;
import fr.efrei.messagerie.entity.Message;
import java.time.LocalDateTime;

/**
 * Représentation d'un message dans l'historique. Mêmes champs que le format imposé sur le
 * canal WebSocket (expediteurId, destinataireId, contenu, dateEnvoi), plus l'identifiant
 * (curseur de pagination, D-006 ; accusés, D-013) et l'état de livraison (D-013, D-016).
 *
 * @param dateEnvoi au format de l'énoncé, à la seconde : « 2026-09-21T10:15:30 »
 */
public record MessageReponse(
        Long id,
        Long expediteurId,
        Long destinataireId,
        String contenu,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime dateEnvoi,
        EtatMessage etat) {

    /**
     * getExpediteur().getId() ne déclenche pas de requête SQL : avec une relation paresseuse,
     * Hibernate connaît déjà l'identifiant (c'est la valeur de la clé étrangère).
     */
    public static MessageReponse depuis(Message message) {
        return new MessageReponse(
                message.getId(),
                message.getExpediteur().getId(),
                message.getDestinataire().getId(),
                message.getContenu(),
                message.getDateEnvoi(),
                message.getEtat());
    }
}
