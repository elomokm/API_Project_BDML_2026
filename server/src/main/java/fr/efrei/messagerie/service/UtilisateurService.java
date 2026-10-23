package fr.efrei.messagerie.service;

import fr.efrei.messagerie.entity.Utilisateur;
import fr.efrei.messagerie.exception.UtilisateurIntrouvableException;
import fr.efrei.messagerie.repository.UtilisateurRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation des utilisateurs.
 */
@Service
public class UtilisateurService {

    private final UtilisateurRepository utilisateurs;

    public UtilisateurService(UtilisateurRepository utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    /** Tous les utilisateurs, par ordre alphabétique de username. */
    @Transactional(readOnly = true)
    public List<Utilisateur> lister() {
        return utilisateurs.findAll(Sort.by("username"));
    }

    /** @throws UtilisateurIntrouvableException si l'identifiant n'existe pas (404) */
    @Transactional(readOnly = true)
    public Utilisateur trouver(Long id) {
        return utilisateurs.findById(id).orElseThrow(() -> new UtilisateurIntrouvableException(id));
    }
}
