package fr.efrei.messagerie.controller;

import fr.efrei.messagerie.dto.UtilisateurStatutReponse;
import fr.efrei.messagerie.entity.Utilisateur;
import fr.efrei.messagerie.service.UtilisateurService;
import fr.efrei.messagerie.websocket.RegistreSessions;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/utilisateurs")
public class UtilisateurController {

    private final UtilisateurService utilisateurService;
    private final RegistreSessions registre;

    public UtilisateurController(UtilisateurService utilisateurService, RegistreSessions registre) {
        this.utilisateurService = utilisateurService;
        this.registre = registre;
    }

    /**
     * Liste des utilisateurs avec leur statut (D-011) : connecté si au moins une connexion
     * WebSocket est ouverte. Consultation ponctuelle en REST, comme l'impose l'énoncé ; les
     * changements en direct arrivent par les événements « presence » du canal.
     */
    @GetMapping
    public List<UtilisateurStatutReponse> lister() {
        return utilisateurService.lister().stream().map(this::avecStatut).toList();
    }

    /**
     * Un utilisateur. C'est l'adresse renvoyée dans l'en-tête Location après une création de
     * compte : elle doit donc exister. 404 si l'identifiant est inconnu.
     */
    @GetMapping("/{id}")
    public UtilisateurStatutReponse trouver(@PathVariable Long id) {
        return avecStatut(utilisateurService.trouver(id));
    }

    private UtilisateurStatutReponse avecStatut(Utilisateur utilisateur) {
        return UtilisateurStatutReponse.depuis(utilisateur, registre.estConnecte(utilisateur.getId()));
    }
}
