package fr.efrei.messagerie;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MessagerieApplicationTests {

	/**
	 * Le contexte démarre : schema.sql s'est exécuté et Hibernate, en mode validate, a jugé
	 * les entités conformes au schéma.
	 */
	@Test
	void contextLoads() {
	}

}
