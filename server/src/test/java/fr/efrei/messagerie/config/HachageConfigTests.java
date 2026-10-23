package fr.efrei.messagerie.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hachage Argon2id (D-007). Test unitaire : aucun contexte Spring ni base nécessaire.
 */
class HachageConfigTests {

    private final PasswordEncoder encodeur = new HachageConfig().encodeurMotDePasse();

    @Test
    void lEmpreinte_indiqueArgon2idEtLesParametresOwasp() {
        String empreinte = encodeur.encode("motdepasse123");

        assertThat(empreinte).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        // Le mot de passe n'apparaît jamais dans ce qui est stocké.
        assertThat(empreinte).doesNotContain("motdepasse123");
    }

    @Test
    void unMemeMotDePasse_donneDeuxEmpreintesDifferentes_graceAuSel() {
        assertThat(encodeur.encode("azerty1234")).isNotEqualTo(encodeur.encode("azerty1234"));
    }

    @Test
    void laVerification_accepteLeBonMotDePasse_etRefuseLesAutres() {
        String empreinte = encodeur.encode("motdepasse123");

        assertThat(encodeur.matches("motdepasse123", empreinte)).isTrue();
        assertThat(encodeur.matches("motdepasse124", empreinte)).isFalse();
    }
}
