package fr.efrei.messagerie.dto;

import fr.efrei.messagerie.entity.Utilisateur;

/**
 * Utilisateur et son statut de connexion, pour GET /api/utilisateurs (énoncé : « lister les
 * utilisateurs et leur statut »). Ajouté à l'étape 4, quand la présence existe (D-006, D-011).
 *
 * @param connecte true si l'utilisateur a au moins une connexion WebSocket ouverte
 */
public record UtilisateurStatutReponse(Long id, String username, boolean connecte) {

    public static UtilisateurStatutReponse depuis(Utilisateur utilisateur, boolean connecte) {
        return new UtilisateurStatutReponse(utilisateur.getId(), utilisateur.getUsername(), connecte);
    }
}
