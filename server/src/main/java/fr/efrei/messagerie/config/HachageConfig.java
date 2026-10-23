package fr.efrei.messagerie.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hachage des mots de passe avec Argon2id (D-007), paramètres recommandés par l'OWASP.
 *
 * Paramètres fixés explicitement plutôt que hérités des valeurs par défaut de Spring
 * (16 Mio), légèrement inférieures à la recommandation.
 */
@Configuration
public class HachageConfig {

    /** Longueur du sel aléatoire, généré pour chaque mot de passe : 16 octets. */
    private static final int LONGUEUR_SEL = 16;
    /** Longueur de l'empreinte produite : 32 octets. */
    private static final int LONGUEUR_EMPREINTE = 32;
    /** Un seul fil de calcul par empreinte, pour ne pas monopoliser les cœurs du serveur. */
    private static final int PARALLELISME = 1;
    /** Mémoire occupée par chaque calcul, en Kio : 19 Mio. C'est ce qui freine les GPU. */
    private static final int MEMOIRE_KIO = 19 * 1024;
    /** Nombre de passes sur cette mémoire. */
    private static final int ITERATIONS = 2;

    /**
     * encode() produit une empreinte de la forme
     * $argon2id$v=19$m=19456,t=2,p=1$<sel>$<empreinte> : algorithme, paramètres et sel y sont
     * inscrits, donc matches() peut vérifier un mot de passe sans rien stocker d'autre.
     */
    @Bean
    PasswordEncoder encodeurMotDePasse() {
        return new Argon2PasswordEncoder(LONGUEUR_SEL, LONGUEUR_EMPREINTE, PARALLELISME,
                MEMOIRE_KIO, ITERATIONS);
    }
}
