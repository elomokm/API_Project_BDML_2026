package fr.efrei.messagerie.exception;

/**
 * Utilisateur demandé inexistant. Traduite en 404 Not Found (D-006).
 */
public class UtilisateurIntrouvableException extends RuntimeException {

    public UtilisateurIntrouvableException(Long id) {
        super("Aucun utilisateur avec l'identifiant " + id + ".");
    }
}
