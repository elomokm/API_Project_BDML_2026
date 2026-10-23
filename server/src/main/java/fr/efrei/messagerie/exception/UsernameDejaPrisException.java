package fr.efrei.messagerie.exception;

/**
 * Création d'un compte avec un username déjà utilisé. Traduite en 409 Conflict (D-006) :
 * la requête est valide, mais elle entre en conflit avec l'état actuel de la ressource.
 */
public class UsernameDejaPrisException extends RuntimeException {

    public UsernameDejaPrisException(String username) {
        super("Le username « " + username + " » est déjà utilisé.");
    }
}
