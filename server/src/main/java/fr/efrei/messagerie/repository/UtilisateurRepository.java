package fr.efrei.messagerie.repository;

import fr.efrei.messagerie.entity.Utilisateur;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès aux utilisateurs. Spring Data génère l'implémentation : save, findById, findAll...
 * ainsi que les requêtes dérivées du nom des méthodes ci-dessous.
 */
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    /** SELECT ... FROM utilisateur WHERE username = ? */
    Optional<Utilisateur> findByUsername(String username);

    /** Vérification rapide avant création (la contrainte UNIQUE reste le dernier rempart). */
    boolean existsByUsername(String username);
}
