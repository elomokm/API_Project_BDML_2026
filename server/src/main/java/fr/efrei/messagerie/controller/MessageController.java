package fr.efrei.messagerie.controller;

import fr.efrei.messagerie.dto.MessageReponse;
import fr.efrei.messagerie.security.UtilisateurConnecte;
import fr.efrei.messagerie.service.MessageService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Historique de la conversation entre l'utilisateur du jeton et {utilisateurId}.
     *
     * Exposé en REST (contrainte de l'énoncé) : c'est une ressource identifiable par une URL,
     * consultable à tout moment, y compris pour des messages envoyés avant la connexion.
     *
     * GET /api/messages/2?avant=150&limite=50 : les 50 messages précédant le message 150.
     * Sans « avant » : les plus récents. « limite » : 1 à 100, 50 par défaut (sinon 400).
     *
     * @param moi injecté par Spring Security à partir du jeton validé par JwtFiltre
     */
    @GetMapping("/{utilisateurId}")
    public List<MessageReponse> historique(
            @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long utilisateurId,
            @RequestParam(required = false) Long avant,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limite) {
        return messageService.historique(moi.id(), utilisateurId, avant, limite).stream()
                .map(MessageReponse::depuis)
                .toList();
    }
}
