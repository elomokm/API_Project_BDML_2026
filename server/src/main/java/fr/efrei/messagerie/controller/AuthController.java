package fr.efrei.messagerie.controller;

import fr.efrei.messagerie.dto.ConnexionReponse;
import fr.efrei.messagerie.dto.ConnexionRequete;
import fr.efrei.messagerie.dto.UtilisateurReponse;
import fr.efrei.messagerie.entity.Utilisateur;
import fr.efrei.messagerie.security.JwtService;
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
    private final JwtService jwtService;

    public AuthController(CompteService compteService, JwtService jwtService) {
        this.compteService = compteService;
        this.jwtService = jwtService;
    }

    /**
     * Connexion : 200 avec un JWT, ou 401 (D-006, D-008).
     * POST et non GET : les identifiants voyagent dans le corps, jamais dans l'URL (qui finit
     * dans les logs, l'historique du navigateur et les proxys).
     */
    @PostMapping("/login")
    public ConnexionReponse connecter(@Valid @RequestBody ConnexionRequete requete) {
        Utilisateur utilisateur = compteService.authentifier(requete);
        return new ConnexionReponse(
                jwtService.generer(utilisateur),
                "Bearer",
                jwtService.dureeEnSecondes(),
                UtilisateurReponse.depuis(utilisateur));
    }
}
