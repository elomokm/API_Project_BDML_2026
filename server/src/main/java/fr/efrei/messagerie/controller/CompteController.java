package fr.efrei.messagerie.controller;

import fr.efrei.messagerie.dto.CreationCompteRequete;
import fr.efrei.messagerie.dto.UtilisateurReponse;
import fr.efrei.messagerie.entity.Utilisateur;
import fr.efrei.messagerie.service.CompteService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comptes")
public class CompteController {

    private final CompteService compteService;

    public CompteController(CompteService compteService) {
        this.compteService = compteService;
    }

    /**
     * Crée un compte : 201 Created et non 200, car une nouvelle ressource existe désormais.
     * L'en-tête Location indique son adresse, consultable par GET.
     * @Valid déclenche la validation du DTO : en cas d'échec, 400 sans entrer dans la méthode.
     */
    @PostMapping
    public ResponseEntity<UtilisateurReponse> creer(@Valid @RequestBody CreationCompteRequete requete) {
        Utilisateur cree = compteService.creer(requete);
        return ResponseEntity
                .created(URI.create("/api/utilisateurs/" + cree.getId()))
                .body(UtilisateurReponse.depuis(cree));
    }
}
