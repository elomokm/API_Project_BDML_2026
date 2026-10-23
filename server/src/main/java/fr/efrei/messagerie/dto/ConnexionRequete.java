package fr.efrei.messagerie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de POST /api/auth/login.
 *
 * Validation volontairement minimale : vérifier ici les règles de création de compte
 * renseignerait un attaquant sur ce qu'est un mot de passe valide. On se contente de bornes
 * qui protègent le serveur (champ vide, texte démesuré à hacher).
 */
public record ConnexionRequete(
        @NotBlank(message = "est obligatoire")
        @Size(max = 50, message = "est trop long")
        String username,

        @NotBlank(message = "est obligatoire")
        @Size(max = 128, message = "est trop long")
        String motDePasse) {

    /** Ne jamais afficher le mot de passe, même dans un log ou un message de débogage. */
    @Override
    public String toString() {
        return "ConnexionRequete[username=" + username + ", motDePasse=***]";
    }
}
