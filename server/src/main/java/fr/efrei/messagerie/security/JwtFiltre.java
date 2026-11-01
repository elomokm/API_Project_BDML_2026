package fr.efrei.messagerie.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtre d'authentification par JWT (D-008), exécuté une fois par requête, avant les
 * contrôleurs.
 *
 * Trajet d'une requête GET /api/messages/2 :
 * 1. le filtre lit l'en-tête « Authorization: Bearer <jeton> » ;
 * 2. JwtService vérifie la signature et l'expiration ;
 * 3. si le jeton est valide, l'utilisateur est placé dans le contexte de sécurité ;
 * 4. la chaîne continue : Spring Security laisse passer la requête vers le contrôleur, qui
 *    reçoit l'utilisateur avec @AuthenticationPrincipal.
 * Si le jeton est absent ou invalide, le contexte reste vide : la chaîne de sécurité refuse
 * alors la requête en 401 avant qu'elle atteigne le contrôleur (voir SecuriteConfig).
 *
 * Volontairement pas un bean Spring (@Component) : Spring Boot enregistrerait alors aussi ce
 * filtre comme filtre de servlet, hors de la chaîne de sécurité. Il s'exécuterait trop tôt, et
 * OncePerRequestFilter l'empêcherait ensuite de s'exécuter dans la chaîne : toutes les
 * requêtes seraient refusées. Il est créé uniquement dans SecuriteConfig.
 */
public class JwtFiltre extends OncePerRequestFilter {

    private static final String PREFIXE = "Bearer ";

    private final JwtService jwtService;

    public JwtFiltre(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        String entete = requete.getHeader(HttpHeaders.AUTHORIZATION);

        if (entete != null && entete.startsWith(PREFIXE)) {
            String jeton = entete.substring(PREFIXE.length());
            jwtService.verifier(jeton).ifPresent(utilisateur -> {
                // Aucun rôle dans ce projet : un utilisateur authentifié a accès à tout ce qui
                // est protégé ; ce qui lui appartient est contrôlé dans les services.
                var authentification = new UsernamePasswordAuthenticationToken(
                        utilisateur, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentification);
            });
        }
        chaine.doFilter(requete, reponse);
    }
}
