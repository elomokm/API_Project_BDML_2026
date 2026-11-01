package fr.efrei.messagerie.exception;

/**
 * Message refusé sur le canal WebSocket (D-012). Le code est stable et lisible par le client
 * (« contenu_vide », « destinataire_inexistant »...) ; il est renvoyé dans un message
 * de type « erreur », sans fermer la connexion.
 */
public class MessageInvalideException extends RuntimeException {

    private final String code;

    public MessageInvalideException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
