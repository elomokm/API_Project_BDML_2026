package fr.efrei.messagerie.controller;

import fr.efrei.messagerie.dto.ConnexionRequete;
import fr.efrei.messagerie.dto.UtilisateurReponse;
import fr.efrei.messagerie.service.CompteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CompteService compteService;

    public AuthController(CompteService compteService) {
        this.compteService = compteService;
    }

    /**
     * Connexion : 200 avec l'utilisateur authentifié, ou 401 (D-006).
     * Provisoire à l'étape 2 : le jeton JWT s'ajoutera à la réponse à l'étape 3.
     * POST et non GET : les identifiants voyagent dans le corps, jamais dans l'URL (qui finit
     * dans les logs, l'historique du navigateur et les proxys).
     */
    @PostMapping("/login")
    public UtilisateurReponse connecter(@Valid @RequestBody ConnexionRequete requete) {
        return UtilisateurReponse.depuis(compteService.authentifier(requete));
    }
}
