package fr.efrei.messagerie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * UserDetailsServiceAutoConfiguration est exclue : par défaut, Spring Boot crée un utilisateur
 * en mémoire avec un mot de passe généré et l'écrit dans les logs. Ici, l'authentification
 * passe uniquement par la base et le JWT (D-008).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MessagerieApplication {

	public static void main(String[] args) {
		SpringApplication.run(MessagerieApplication.class, args);
	}

}
