package fr.efrei.messagerie.websocket;

import fr.efrei.messagerie.entity.Message;
import fr.efrei.messagerie.exception.MessageInvalideException;
import fr.efrei.messagerie.security.UtilisateurConnecte;
import fr.efrei.messagerie.service.MessageService;
import fr.efrei.messagerie.websocket.Evenements.EvenementErreur;
import fr.efrei.messagerie.websocket.Evenements.EvenementMessage;
import fr.efrei.messagerie.websocket.Evenements.EvenementPresence;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Gestionnaire du canal /ws/messages (D-009) : cycle de vie des connexions et aiguillage des
 * messages reçus selon leur « type ».
 *
 * Trajet d'un message de A vers B :
 * 1. A envoie {"type":"message","destinataireId":B,"contenu":"..."} ;
 * 2. l'expéditeur est A, l'utilisateur authentifié au handshake, jamais le contenu du message ;
 * 3. MessageService valide puis enregistre le message (étape 1 de l'énoncé : persister) ;
 * 4. le message enregistré est poussé sur toutes les sessions de B s'il est connecté (étape 2 :
 *    retransmettre), et renvoyé en écho sur toutes les sessions de A ;
 * 5. si B est hors ligne, le message reste ENVOYE en base et lui sera poussé à sa prochaine
 *    connexion (D-013).
 */
@Component
public class MessagerieHandler extends TextWebSocketHandler {

    /** Délai maximal d'un envoi vers un client lent, avant fermeture de sa session. */
    private static final int DELAI_ENVOI_MS = 5_000;
    /** Volume maximal en attente pour un client lent, avant fermeture de sa session. */
    private static final int TAMPON_ENVOI_OCTETS = 64 * 1024;
    /** Trame maximale acceptée (D-012) : au-delà, la connexion est fermée (code 1009). */
    private static final int TAILLE_MAX_TRAME_OCTETS = 16 * 1024;

    private final RegistreSessions registre;
    private final MessageService messageService;
    private final ObjectMapper json;

    public MessagerieHandler(RegistreSessions registre, MessageService messageService,
                             ObjectMapper json) {
        this.registre = registre;
        this.messageService = messageService;
        this.json = json;
    }

    // Cycle de vie de la connexion

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UtilisateurConnecte moi = utilisateur(session);
        // Garde-fou mémoire : une trame démesurée est refusée par le conteneur avant même
        // d'être lue, et la connexion est fermée (1009, message trop gros).
        session.setTextMessageSizeLimit(TAILLE_MAX_TRAME_OCTETS);
        // Une session WebSocket n'accepte qu'un envoi à la fois. Plusieurs fils peuvent
        // vouloir écrire en même temps (messages de plusieurs expéditeurs, présence) : le
        // décorateur les met en file, avec un délai et un tampon bornés.
        WebSocketSession sure = new ConcurrentWebSocketSessionDecorator(
                session, DELAI_ENVOI_MS, TAMPON_ENVOI_OCTETS);

        boolean premiereSession = registre.ajouter(moi.id(), sure);
        if (premiereSession) {
            registre.envoyerAuxAutres(moi.id(), versJson(EvenementPresence.de(moi.id(), true)));
        }
        // Rattrapage (D-013) : messages reçus pendant l'absence, dans l'ordre d'arrivée. Ils
        // restent ENVOYE tant que le client n'en a pas accusé réception.
        for (Message enAttente : messageService.nonLivres(moi.id())) {
            registre.envoyer(sure, versJson(EvenementMessage.depuis(enAttente)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus statut) {
        UtilisateurConnecte moi = utilisateur(session);
        boolean derniereSession = registre.retirer(moi.id(), session.getId());
        if (derniereSession) {
            registre.envoyerAuxAutres(moi.id(), versJson(EvenementPresence.de(moi.id(), false)));
        }
    }

    // Réception

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage texte) {
        UtilisateurConnecte moi = utilisateur(session);
        WebSocketSession sure = registre.session(moi.id(), session.getId());
        try {
            JsonNode recu = lire(texte.getPayload());
            JsonNode type = recu.get("type");
            String nomType = type != null && type.isString() ? type.stringValue() : "";
            switch (nomType) {
                case "message" -> recevoirMessage(moi, recu);
                case "accuse" -> recevoirAccuse(moi, recu);
                default -> throw new MessageInvalideException("type_inconnu",
                        "Type de message absent ou inconnu.");
            }
        } catch (MessageInvalideException e) {
            // Erreur signalée sur le canal, connexion maintenue (D-012).
            registre.envoyer(sure, versJson(EvenementErreur.de(e.getCode(), e.getMessage())));
        }
    }

    private void recevoirMessage(UtilisateurConnecte moi, JsonNode recu) {
        // Le serveur ne croit pas l'expéditeur annoncé par le client : il doit être absent, ou
        // égal à l'utilisateur authentifié. Une usurpation est refusée et rendue visible.
        JsonNode expediteurAnnonce = recu.get("expediteurId");
        if (expediteurAnnonce != null && !(expediteurAnnonce.isIntegralNumber()
                && expediteurAnnonce.asLong() == moi.id())) {
            throw new MessageInvalideException("expediteur_invalide",
                    "L'expéditeur doit être l'utilisateur connecté.");
        }
        JsonNode destinataire = recu.get("destinataireId");
        if (destinataire == null || !destinataire.isIntegralNumber()) {
            throw new MessageInvalideException("destinataire_invalide",
                    "destinataireId doit être un identifiant numérique.");
        }
        JsonNode contenu = recu.get("contenu");
        // Une éventuelle « dateEnvoi » envoyée par le client n'est même pas lue.

        // (1) Persister : le message n'est retransmis qu'une fois enregistré.
        Message enregistre = messageService.envoyer(moi.id(), destinataire.asLong(),
                contenu != null && contenu.isString() ? contenu.stringValue() : null);

        // (2) Retransmettre au destinataire s'il est connecté, et à tous les appareils de
        // l'expéditeur (écho : confirmation d'enregistrement et synchronisation).
        String evenement = versJson(EvenementMessage.depuis(enregistre));
        registre.envoyer(enregistre.getDestinataire().getId(), evenement);
        registre.envoyer(moi.id(), evenement);
    }

    private void recevoirAccuse(UtilisateurConnecte moi, JsonNode recu) {
        JsonNode messageId = recu.get("messageId");
        if (messageId == null || !messageId.isIntegralNumber()) {
            throw new MessageInvalideException("message_invalide",
                    "messageId doit être un identifiant numérique.");
        }
        messageService.accuserReception(moi.id(), messageId.asLong());
    }

    // Outils

    private JsonNode lire(String texte) {
        try {
            JsonNode noeud = json.readTree(texte);
            if (noeud == null || !noeud.isObject()) {
                throw new MessageInvalideException("json_invalide", "Un objet JSON est attendu.");
            }
            return noeud;
        } catch (JacksonException e) {
            throw new MessageInvalideException("json_invalide", "Le message n'est pas du JSON valide.");
        }
    }

    private String versJson(Object evenement) {
        return json.writeValueAsString(evenement);
    }

    private static UtilisateurConnecte utilisateur(WebSocketSession session) {
        return (UtilisateurConnecte) session.getAttributes()
                .get(JwtHandshakeIntercepteur.ATTRIBUT_UTILISATEUR);
    }
}
