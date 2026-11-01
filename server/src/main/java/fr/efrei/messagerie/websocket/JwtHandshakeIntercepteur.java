package fr.efrei.messagerie.websocket;

import fr.efrei.messagerie.security.JwtService;
import fr.efrei.messagerie.security.UtilisateurConnecte;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Authentification à l'ouverture du canal (D-010).
 *
 * Le navigateur ne permet pas d'ajouter l'en-tête Authorization au handshake WebSocket ; le
 * seul en-tête qu'il laisse choisir est Sec-WebSocket-Protocol. Le client y propose donc deux
 * valeurs : new WebSocket(url, ["messagerie.v1", jeton]).
 *
 * Cet intercepteur s'exécute pendant le handshake, avant l'ouverture du canal : sans jeton
 * valide, le serveur répond 401 et la connexion n'existe jamais. Avec un jeton valide,
 * l'identité vérifiée est attachée à la session : tout ce qui arrivera ensuite sur ce canal
 * sera attribué à cet utilisateur.
 */
public class JwtHandshakeIntercepteur implements HandshakeInterceptor {

    /** Sous-protocole de l'application, seul renvoyé au client (jamais le jeton). */
    public static final String SOUS_PROTOCOLE = "messagerie.v1";
    /** Clé de l'identité dans les attributs de la session WebSocket. */
    public static final String ATTRIBUT_UTILISATEUR = "utilisateur";

    private static final String ENTETE_PROTOCOLE = "Sec-WebSocket-Protocol";

    private final JwtService jwtService;

    public JwtHandshakeIntercepteur(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest requete, ServerHttpResponse reponse,
                                   WebSocketHandler gestionnaire, Map<String, Object> attributs) {
        // L'en-tête arrive sous la forme « messagerie.v1, eyJhbGciOi... » : une liste.
        List<String> protocoles = requete.getHeaders().getValuesAsList(ENTETE_PROTOCOLE);

        Optional<UtilisateurConnecte> utilisateur = protocoles.contains(SOUS_PROTOCOLE)
                ? protocoles.stream()
                        .filter(valeur -> !valeur.equals(SOUS_PROTOCOLE))
                        .findFirst()
                        .flatMap(jwtService::verifier)
                : Optional.empty();

        if (utilisateur.isEmpty()) {
            reponse.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false; // handshake refusé : aucune connexion n'est ouverte
        }
        attributs.put(ATTRIBUT_UTILISATEUR, utilisateur.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest requete, ServerHttpResponse reponse,
                               WebSocketHandler gestionnaire, Exception exception) {
        // Rien à faire après le handshake.
    }
}
