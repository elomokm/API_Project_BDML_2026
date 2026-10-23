package fr.efrei.messagerie.repository;

import fr.efrei.messagerie.entity.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès aux messages.
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Conversation entre deux utilisateurs, dans les deux sens, du plus ancien au plus récent.
     *
     * Une condition sur l'expéditeur seul ne suffirait pas : elle ne renverrait que la moitié
     * de la conversation (les messages de A, sans les réponses de B).
     * Le tri secondaire par id départage deux messages enregistrés à la même date.
     *
     * Requête écrite explicitement (D-002) plutôt que dérivée du nom de la méthode, pour que
     * la logique soit lisible et défendable.
     */
    @Query("""
            SELECT m FROM Message m
            WHERE (m.expediteur.id = :utilisateurA AND m.destinataire.id = :utilisateurB)
               OR (m.expediteur.id = :utilisateurB AND m.destinataire.id = :utilisateurA)
            ORDER BY m.dateEnvoi, m.id
            """)
    List<Message> trouverConversation(@Param("utilisateurA") Long utilisateurA,
                                      @Param("utilisateurB") Long utilisateurB);
}
