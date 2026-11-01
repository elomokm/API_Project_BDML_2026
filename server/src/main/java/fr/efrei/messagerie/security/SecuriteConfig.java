package fr.efrei.messagerie.security;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Chaîne de sécurité (D-008) : sans état, protégée par JWT.
 *
 * Seules la création de compte et la connexion sont publiques ; tout le reste exige un jeton
 * valide, sinon 401 (contrainte de l'énoncé).
 */
@Configuration
public class SecuriteConfig {

    @Bean
    SecurityFilterChain chaineDeSecurite(HttpSecurity http, JwtService jwtService,
                                         CorsConfigurationSource reglesCors) throws Exception {
        http
                // CORS (D-015) : règles passées explicitement (le bean ci-dessous). Les
                // requêtes de vérification préalable (OPTIONS) du navigateur sont traitées
                // avant l'authentification, et une origine non listée est refusée (403).
                .cors(cors -> cors.configurationSource(reglesCors))
                // Sans état : aucune session HTTP, chaque requête porte son propre jeton.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF désactivé : cette attaque exploite un cookie envoyé automatiquement par
                // le navigateur. Ici, aucun cookie n'authentifie : le client joint le jeton
                // lui-même dans l'en-tête Authorization.
                .csrf(AbstractHttpConfigurer::disable)
                // Pas de formulaire de connexion ni d'authentification Basic : le seul moyen
                // de s'authentifier est le JWT.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(regles -> regles
                        .requestMatchers(HttpMethod.POST, "/api/comptes", "/api/auth/login").permitAll()
                        // Canal WebSocket : le navigateur ne peut pas y joindre l'en-tête
                        // Authorization. Le jeton est vérifié par JwtHandshakeIntercepteur,
                        // pendant le handshake, qui refuse en 401 sans jeton valide (D-010).
                        .requestMatchers(HttpMethod.GET, "/ws/messages").permitAll()
                        // Page d'erreur interne de Spring : sans cela, une erreur sur une route
                        // publique serait masquée par un 401.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                // Le filtre JWT s'exécute avant le filtre standard d'authentification.
                .addFilterBefore(new JwtFiltre(jwtService), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(erreurs -> erreurs.authenticationEntryPoint(refusNonAuthentifie()));
        return http.build();
    }

    /**
     * Origines autorisées à appeler l'API depuis un navigateur (D-015).
     *
     * Le navigateur interdit par défaut à une page d'appeler une autre origine (ici, le port
     * 8081 des clients vers le port 8080 de l'API). Le serveur lève cette interdiction
     * uniquement pour les origines listées, jamais pour toutes (« * »).
     * Pas de cookies : allowCredentials reste à false, le jeton est joint explicitement.
     */
    @Bean
    CorsConfigurationSource reglesCors(@Value("${app.cors.origines}") List<String> origines) {
        CorsConfiguration regles = new CorsConfiguration();
        regles.setAllowedOriginPatterns(origines);
        regles.setAllowedMethods(List.of("GET", "POST"));
        regles.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Sans cela, le client ne pourrait pas lire l'en-tête Location d'un 201.
        regles.setExposedHeaders(List.of("Location"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", regles);
        return source;
    }

    /**
     * Réponse quand une requête protégée arrive sans jeton valide : 401 au format
     * ProblemDetail (D-006), avec l'en-tête WWW-Authenticate qui indique le schéma attendu
     * (RFC 6750). Même réponse quel que soit le motif (absent, expiré, altéré).
     */
    private static AuthenticationEntryPoint refusNonAuthentifie() {
        return (requete, reponse, exception) -> {
            reponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            reponse.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            reponse.getWriter().write("""
                    {"title":"Authentification requise","status":401,\
                    "detail":"Un jeton JWT valide est nécessaire.","instance":"%s"}"""
                    .formatted(requete.getRequestURI()));
        };
    }
}
