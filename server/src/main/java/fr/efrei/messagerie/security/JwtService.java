package fr.efrei.messagerie.security;

import fr.efrei.messagerie.entity.Utilisateur;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Émission et vérification des JWT (D-008), avec jjwt comme au TP2.
 *
 * Un jeton a trois parties séparées par des points : en-tête.charge.signature.
 * L'en-tête et la charge sont seulement encodés en Base64URL (lisibles par n'importe qui) ;
 * la signature est un HMAC-SHA256 de « en-tête.charge » calculé avec le secret du serveur.
 * Sans le secret, impossible de produire une signature valide pour une charge modifiée.
 */
@Service
public class JwtService {

    /** Nom de la revendication qui porte le username, en plus du « sub » standard. */
    private static final String REVENDICATION_USERNAME = "username";

    private final SecretKey cle;
    private final Duration duree;
    private final Clock horloge;

    /**
     * @param secretBase64 secret de 256 bits au moins, encodé en Base64 (variable JWT_SECRET).
     *                     Keys.hmacShaKeyFor refuse une clé trop courte pour HS256 : le
     *                     serveur ne démarre pas avec un secret faible.
     */
    public JwtService(@Value("${app.jwt.secret}") String secretBase64,
                      @Value("${app.jwt.duree}") Duration duree,
                      Clock horloge) {
        this.cle = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
        this.duree = duree;
        this.horloge = horloge;
    }

    /**
     * Fabrique le jeton remis au client après un login réussi.
     * Charge : sub (identifiant, stable), username, iat (émission), exp (expiration).
     */
    public String generer(Utilisateur utilisateur) {
        Instant maintenant = horloge.instant();
        return Jwts.builder()
                .subject(String.valueOf(utilisateur.getId()))
                .claim(REVENDICATION_USERNAME, utilisateur.getUsername())
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(duree)))
                // HS256, déduit de la taille de la clé (256 bits).
                .signWith(cle, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Vérifie un jeton et en extrait l'identité.
     *
     * parseSignedClaims recalcule la signature avec le secret et la compare à celle du
     * jeton : une charge modifiée, une signature altérée ou un jeton expiré lèvent une
     * JwtException. Le jeton n'est accepté que s'il est signé (un jeton « alg: none » est
     * refusé par verifyWith).
     *
     * @return l'utilisateur du jeton, ou vide si le jeton est invalide pour quelque raison
     */
    public Optional<UtilisateurConnecte> verifier(String jeton) {
        try {
            Claims charge = Jwts.parser()
                    .verifyWith(cle)
                    // Même horloge que pour l'émission : l'expiration est testable.
                    .clock(() -> Date.from(horloge.instant()))
                    .build()
                    .parseSignedClaims(jeton)
                    .getPayload();
            return Optional.of(new UtilisateurConnecte(
                    Long.valueOf(charge.getSubject()),
                    charge.get(REVENDICATION_USERNAME, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            // Volontairement silencieux : le motif du refus n'est jamais renvoyé au client,
            // et le jeton n'est jamais écrit dans les logs.
            return Optional.empty();
        }
    }

    /** Durée de vie d'un jeton, en secondes (renvoyée au client avec le jeton). */
    public long dureeEnSecondes() {
        return duree.toSeconds();
    }
}
