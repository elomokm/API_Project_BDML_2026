package fr.efrei.messagerie.dto;

/**
 * Réponse de POST /api/auth/login (D-008).
 *
 * @param jeton       JWT à joindre aux requêtes suivantes : « Authorization: Bearer <jeton> »
 * @param typeJeton   toujours « Bearer » (schéma d'authentification de la RFC 6750)
 * @param expireDans  durée de validité du jeton, en secondes
 * @param utilisateur l'utilisateur authentifié
 */
public record ConnexionReponse(String jeton, String typeJeton, long expireDans,
                               UtilisateurReponse utilisateur) {
}
