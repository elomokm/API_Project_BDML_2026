package fr.efrei.messagerie.websocket;

import fr.efrei.messagerie.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * Déclaration du canal temps réel /ws/messages (D-009, D-010).
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MessagerieHandler gestionnaire;
    private final JwtService jwtService;
    private final String[] origines;

    public WebSocketConfig(MessagerieHandler gestionnaire, JwtService jwtService,
                           @Value("${app.cors.origines}") String[] origines) {
        this.gestionnaire = gestionnaire;
        this.jwtService = jwtService;
        this.origines = origines;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registre) {
        // Négociation du sous-protocole : parmi les valeurs proposées par le client
        // (« messagerie.v1 » et le jeton), seul « messagerie.v1 » est reconnu, donc seul
        // renvoyé. Le navigateur exige que le serveur en retienne un : sinon il ferme.
        DefaultHandshakeHandler negociation = new DefaultHandshakeHandler();
        negociation.setSupportedProtocols(JwtHandshakeIntercepteur.SOUS_PROTOCOLE);

        registre.addHandler(gestionnaire, "/ws/messages")
                .addInterceptors(new JwtHandshakeIntercepteur(jwtService))
                .setHandshakeHandler(negociation)
                // Le navigateur n'applique pas CORS au WebSocket : c'est le serveur qui
                // vérifie l'en-tête Origin du handshake, avec la même liste que l'API (D-015).
                // Un client hors navigateur (sans Origin) n'est pas concerné : il lui faut
                // de toute façon un jeton valide.
                .setAllowedOriginPatterns(origines);
    }
}
