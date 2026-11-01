package fr.efrei.messagerie.security;

/**
 * Identité extraite d'un jeton valide. C'est ce que le filtre place dans le contexte de
 * sécurité, et ce que les contrôleurs reçoivent avec @AuthenticationPrincipal.
 * Aucune donnée sensible : seulement ce que contient le jeton.
 */
public record UtilisateurConnecte(Long id, String username) {
}
