package fr.efrei.messagerie.exception;

/**
 * Échec de connexion. Traduite en 401 Unauthorized (D-006).
 *
 * Volontairement unique pour les deux causes (username inconnu, mot de passe faux) et sans
 * message variable : un attaquant ne doit pas pouvoir distinguer un compte qui existe d'un
 * compte qui n'existe pas.
 */
public class IdentifiantsInvalidesException extends RuntimeException {

    public IdentifiantsInvalidesException() {
        super("Identifiants incorrects.");
    }
}
