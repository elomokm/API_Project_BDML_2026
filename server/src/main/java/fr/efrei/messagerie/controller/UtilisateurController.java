package fr.efrei.messagerie.controller;

import fr.efrei.messagerie.dto.UtilisateurReponse;
import fr.efrei.messagerie.service.UtilisateurService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/utilisateurs")
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    public UtilisateurController(UtilisateurService utilisateurService) {
        this.utilisateurService = utilisateurService;
    }

    /** Liste des utilisateurs (le statut connecté s'ajoutera à l'étape 4). */
    @GetMapping
    public List<UtilisateurReponse> lister() {
        return utilisateurService.lister().stream().map(UtilisateurReponse::depuis).toList();
    }

    /**
     * Un utilisateur. C'est l'adresse renvoyée dans l'en-tête Location après une création de
     * compte : elle doit donc exister. 404 si l'identifiant est inconnu.
     */
    @GetMapping("/{id}")
    public UtilisateurReponse trouver(@PathVariable Long id) {
        return UtilisateurReponse.depuis(utilisateurService.trouver(id));
    }
}
