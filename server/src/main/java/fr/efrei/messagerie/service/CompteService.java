package fr.efrei.messagerie.service;

import fr.efrei.messagerie.dto.ConnexionRequete;
import fr.efrei.messagerie.dto.CreationCompteRequete;
import fr.efrei.messagerie.entity.Utilisateur;
import fr.efrei.messagerie.exception.IdentifiantsInvalidesException;
import fr.efrei.messagerie.exception.UsernameDejaPrisException;
import fr.efrei.messagerie.repository.UtilisateurRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Création de compte et vérification des identifiants.
 */
@Service
public class CompteService {

    private final UtilisateurRepository utilisateurs;
    private final PasswordEncoder encodeur;

    /**
     * Empreinte d'un mot de passe aléatoire, calculée une fois au démarrage. Sert à la
     * connexion d'un username inconnu : on vérifie quand même un mot de passe, contre cette
     * empreinte, pour que la réponse prenne le même temps qu'avec un compte existant (D-006).
     */
    private final String empreinteLeurre;

    public CompteService(UtilisateurRepository utilisateurs, PasswordEncoder encodeur) {
        this.utilisateurs = utilisateurs;
        this.encodeur = encodeur;
        this.empreinteLeurre = encodeur.encode(UUID.randomUUID().toString());
    }

    /**
     * Crée un compte. Le mot de passe est haché avant d'atteindre la base (D-007).
     *
     * @throws UsernameDejaPrisException si le username existe déjà (409)
     */
    @Transactional
    public Utilisateur creer(CreationCompteRequete requete) {
        // 1. Vérification préalable : couvre le cas courant avec un message clair.
        if (utilisateurs.existsByUsername(requete.username())) {
            throw new UsernameDejaPrisException(requete.username());
        }
        String empreinte = encodeur.encode(requete.motDePasse());
        try {
            // saveAndFlush : l'INSERT part immédiatement, donc une violation de contrainte
            // est levée ici, où on peut la traduire, et pas plus tard au commit.
            return utilisateurs.saveAndFlush(new Utilisateur(requete.username(), empreinte));
        } catch (DataIntegrityViolationException e) {
            // 2. Dernier rempart : deux inscriptions simultanées du même username ont pu
            // passer toutes deux la vérification ; la contrainte UNIQUE de la base tranche.
            throw new UsernameDejaPrisException(requete.username());
        }
    }

    /**
     * Vérifie un couple username et mot de passe.
     *
     * @throws IdentifiantsInvalidesException si le compte n'existe pas ou si le mot de passe
     *         est faux, sans distinguer les deux cas (401)
     */
    @Transactional(readOnly = true)
    public Utilisateur authentifier(ConnexionRequete requete) {
        Optional<Utilisateur> utilisateur = utilisateurs.findByUsername(requete.username());
        // Toujours un calcul Argon2, que le compte existe ou non : même temps de réponse.
        String empreinte = utilisateur.map(Utilisateur::getMotDePasse).orElse(empreinteLeurre);
        boolean motDePasseCorrect = encodeur.matches(requete.motDePasse(), empreinte);

        if (utilisateur.isEmpty() || !motDePasseCorrect) {
            throw new IdentifiantsInvalidesException();
        }
        return utilisateur.get();
    }
}
