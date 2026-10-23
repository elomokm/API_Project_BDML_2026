package fr.efrei.messagerie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corps de POST /api/comptes. Règles de validation fixées par D-006 ; un corps qui ne les
 * respecte pas est rejeté en 400 avant d'atteindre le service.
 *
 * @param username 3 à 50 caractères, lettres, chiffres, « . », « _ » et « - » uniquement :
 *                 pas d'espaces ni de caractères invisibles permettant d'imiter un autre compte
 * @param motDePasse 8 à 128 caractères ; la borne haute évite de faire hacher un texte énorme
 */
public record CreationCompteRequete(
        @NotBlank(message = "est obligatoire")
        @Size(min = 3, max = 50, message = "doit contenir entre 3 et 50 caractères")
        @Pattern(regexp = "^[A-Za-z0-9._-]*$",
                 message = "ne peut contenir que des lettres, des chiffres, « . », « _ » et « - »")
        String username,

        @NotBlank(message = "est obligatoire")
        @Size(min = 8, max = 128, message = "doit contenir entre 8 et 128 caractères")
        String motDePasse) {

    /** Ne jamais afficher le mot de passe, même dans un log ou un message de débogage. */
    @Override
    public String toString() {
        return "CreationCompteRequete[username=" + username + ", motDePasse=***]";
    }
}
