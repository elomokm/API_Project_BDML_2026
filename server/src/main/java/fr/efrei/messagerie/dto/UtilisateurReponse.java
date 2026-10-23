package fr.efrei.messagerie.dto;

import fr.efrei.messagerie.entity.Utilisateur;

/**
 * Représentation publique d'un utilisateur. Conversion écrite à la main (D-006) : seuls les
 * champs listés ici sortent de l'API, l'empreinte du mot de passe n'en fait jamais partie.
 * Le statut connecté s'ajoutera à l'étape 4.
 */
public record UtilisateurReponse(Long id, String username) {

    public static UtilisateurReponse depuis(Utilisateur utilisateur) {
        return new UtilisateurReponse(utilisateur.getId(), utilisateur.getUsername());
    }
}
