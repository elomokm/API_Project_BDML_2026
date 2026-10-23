package fr.efrei.messagerie;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de test (D-004) : un vrai PostgreSQL, de la même version que docker-compose.yml,
 * démarré dans un conteneur jetable pour la durée des tests.
 * @ServiceConnection branche automatiquement la datasource de Spring sur ce conteneur,
 * à la place de la base de développement : les tests ne touchent jamais à tes données.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }
}
