package fr.efrei.messagerie.websocket;

import fr.efrei.messagerie.security.JwtService;
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

    public WebSocketConfig(MessagerieHandler gestionnaire, JwtService jwtService) {
        this.gestionnaire = gestionnaire;
        this.jwtService = jwtService;
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
                .setHandshakeHandler(negociation);
        // Origines autorisées : par défaut, uniquement la même origine que le serveur. Les
        // origines des clients seront ajoutées à l'étape 5 (D-015).
    }
}
