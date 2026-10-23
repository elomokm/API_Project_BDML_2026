package fr.efrei.messagerie.repository;

import fr.efrei.messagerie.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès aux utilisateurs. Spring Data génère l'implémentation : save, findById, findAll...
 */
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
}
