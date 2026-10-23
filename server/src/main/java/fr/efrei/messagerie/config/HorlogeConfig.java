package fr.efrei.messagerie.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Horloge unique du serveur, réglée sur le fuseau configuré (D-005).
 *
 * dateEnvoi est une date sans fuseau : sa valeur dépend du fuseau dans lequel elle est
 * calculée. En passant toujours par cette horloge (LocalDateTime.now(horloge)) plutôt que par
 * LocalDateTime.now(), l'horodatage ne dépend plus du fuseau de la machine qui fait tourner
 * le serveur. Les tests peuvent aussi la remplacer par une horloge fixe.
 */
@Configuration
public class HorlogeConfig {

    @Bean
    Clock horloge(@Value("${app.fuseau}") String fuseau) {
        return Clock.system(ZoneId.of(fuseau));
    }
}
