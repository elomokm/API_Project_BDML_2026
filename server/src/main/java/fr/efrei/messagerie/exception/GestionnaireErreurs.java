package fr.efrei.messagerie.exception;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Point unique de traduction des erreurs en réponses HTTP (D-006).
 *
 * Toutes les erreurs sortent au format ProblemDetail (RFC 9457) :
 * {"type", "title", "status", "detail", "instance"}, en application/problem+json.
 *
 * La classe parente, ResponseEntityExceptionHandler, traite déjà les erreurs propres à Spring
 * MVC au même format : JSON malformé (400), méthode non autorisée (405), etc.
 */
@RestControllerAdvice
public class GestionnaireErreurs extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GestionnaireErreurs.class);

    @ExceptionHandler(UsernameDejaPrisException.class)
    ProblemDetail usernameDejaPris(UsernameDejaPrisException e) {
        return probleme(HttpStatus.CONFLICT, "Username déjà pris", e.getMessage());
    }

    @ExceptionHandler(IdentifiantsInvalidesException.class)
    ProblemDetail identifiantsInvalides(IdentifiantsInvalidesException e) {
        return probleme(HttpStatus.UNAUTHORIZED, "Authentification refusée", e.getMessage());
    }

    @ExceptionHandler(UtilisateurIntrouvableException.class)
    ProblemDetail utilisateurIntrouvable(UtilisateurIntrouvableException e) {
        return probleme(HttpStatus.NOT_FOUND, "Utilisateur introuvable", e.getMessage());
    }

    /**
     * Filet de sécurité : toute erreur imprévue devient un 500 générique. Le détail (trace,
     * SQL, nom de classe) est écrit dans les logs du serveur, jamais renvoyé au client.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail erreurInterne(Exception e) {
        LOG.error("Erreur non gérée", e);
        return probleme(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne",
                "Une erreur inattendue est survenue.");
    }

    /**
     * Échec de Bean Validation sur un corps annoté @Valid : 400, avec la liste des champs
     * fautifs dans une propriété supplémentaire « erreurs ».
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        ProblemDetail probleme = probleme(HttpStatus.BAD_REQUEST, "Requête invalide",
                "Un ou plusieurs champs ne respectent pas les règles attendues.");
        List<Map<String, String>> erreurs = e.getBindingResult().getFieldErrors().stream()
                .map(erreur -> Map.of(
                        "champ", erreur.getField(),
                        "message", String.valueOf(erreur.getDefaultMessage())))
                .toList();
        probleme.setProperty("erreurs", erreurs);
        return ResponseEntity.badRequest().body(probleme);
    }

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(statut, detail);
        probleme.setTitle(titre);
        return probleme;
    }
}
