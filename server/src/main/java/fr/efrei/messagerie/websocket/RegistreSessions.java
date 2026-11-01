package fr.efrei.messagerie.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Registre des connexions WebSocket ouvertes (D-011) : utilisateur -> ses sessions.
 *
 * C'est ce qui permet de router un message : pour joindre le destinataire, on cherche ses
 * sessions ici. C'est aussi la source de la présence : un utilisateur est connecté tant qu'il
 * lui reste au moins une session.
 *
 * État en mémoire, volontairement : il décrit des connexions qui meurent avec le serveur. Après
 * un redémarrage, les clients se reconnectent et le registre se reconstruit.
 *
 * Structures concurrentes : chaque connexion est gérée par son propre fil, plusieurs peuvent
 * s'ouvrir, se fermer ou envoyer en même temps.
 */
@Component
public class RegistreSessions {

    private static final Logger LOG = LoggerFactory.getLogger(RegistreSessions.class);

    /** identifiant utilisateur -> (identifiant de session -> session). */
    private final Map<Long, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /**
     * Ajoute une session.
     *
     * @return true si c'est la première session de cet utilisateur (il vient de se connecter)
     */
    public boolean ajouter(Long utilisateurId, WebSocketSession session) {
        AtomicBoolean premiere = new AtomicBoolean(false);
        // compute est atomique pour une même clé : deux connexions simultanées du même
        // utilisateur ne peuvent pas toutes deux se croire « premières ».
        sessions.compute(utilisateurId, (id, siennes) -> {
            if (siennes == null) {
                siennes = new ConcurrentHashMap<>();
                premiere.set(true);
            }
            siennes.put(session.getId(), session);
            return siennes;
        });
        return premiere.get();
    }

    /**
     * Retire une session.
     *
     * @return true si c'était la dernière session de cet utilisateur (il vient de se déconnecter)
     */
    public boolean retirer(Long utilisateurId, String sessionId) {
        AtomicBoolean derniere = new AtomicBoolean(false);
        sessions.computeIfPresent(utilisateurId, (id, siennes) -> {
            siennes.remove(sessionId);
            if (siennes.isEmpty()) {
                derniere.set(true);
                return null; // retire l'utilisateur du registre
            }
            return siennes;
        });
        return derniere.get();
    }

    public boolean estConnecte(Long utilisateurId) {
        return sessions.containsKey(utilisateurId);
    }

    /** La session enregistrée (enveloppée pour l'envoi concurrent), ou null. */
    public WebSocketSession session(Long utilisateurId, String sessionId) {
        Map<String, WebSocketSession> siennes = sessions.get(utilisateurId);
        return siennes == null ? null : siennes.get(sessionId);
    }

    /** Envoie sur toutes les sessions d'un utilisateur ; ne fait rien s'il est hors ligne. */
    public void envoyer(Long utilisateurId, String json) {
        Map<String, WebSocketSession> siennes = sessions.get(utilisateurId);
        if (siennes != null) {
            siennes.values().forEach(session -> envoyer(session, json));
        }
    }

    /** Envoie à tous les utilisateurs connectés, sauf un (événements de présence). */
    public void envoyerAuxAutres(Long exclu, String json) {
        sessions.forEach((utilisateurId, siennes) -> {
            if (!utilisateurId.equals(exclu)) {
                siennes.values().forEach(session -> envoyer(session, json));
            }
        });
    }

    /**
     * Un échec d'envoi (client parti, client trop lent) ne doit pas empêcher les autres
     * destinataires de recevoir le message : il est journalisé, sans le contenu.
     */
    public void envoyer(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Envoi impossible sur la session {} : {}", session.getId(), e.getMessage());
        }
    }
}
