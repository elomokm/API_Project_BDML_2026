package fr.efrei.messagerie.repository;

import fr.efrei.messagerie.entity.Message;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès aux messages.
 *
 * Les deux requêtes de conversation sont écrites explicitement (D-002). Une condition sur
 * l'expéditeur seul ne suffirait pas : elle ne renverrait que la moitié de la conversation
 * (les messages de A, sans les réponses de B). D'où les deux sens reliés par OR.
 *
 * Pagination par curseur (D-006) : on lit du plus récent au plus ancien (id décroissant) et
 * le Pageable ne sert qu'à limiter le nombre de lignes (LIMIT). Le service remet ensuite les
 * messages dans l'ordre chronologique.
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Les messages les plus récents de la conversation entre deux utilisateurs. */
    @Query("""
            SELECT m FROM Message m
            WHERE (m.expediteur.id = :utilisateurA AND m.destinataire.id = :utilisateurB)
               OR (m.expediteur.id = :utilisateurB AND m.destinataire.id = :utilisateurA)
            ORDER BY m.id DESC
            """)
    List<Message> trouverDerniers(@Param("utilisateurA") Long utilisateurA,
                                  @Param("utilisateurB") Long utilisateurB,
                                  Pageable limite);

    /** Les messages de la conversation antérieurs au message « avant » (curseur). */
    @Query("""
            SELECT m FROM Message m
            WHERE ((m.expediteur.id = :utilisateurA AND m.destinataire.id = :utilisateurB)
                OR (m.expediteur.id = :utilisateurB AND m.destinataire.id = :utilisateurA))
              AND m.id < :avant
            ORDER BY m.id DESC
            """)
    List<Message> trouverAvant(@Param("utilisateurA") Long utilisateurA,
                               @Param("utilisateurB") Long utilisateurB,
                               @Param("avant") Long avant,
                               Pageable limite);

    /**
     * Messages d'un destinataire qui n'ont atteint aucun de ses appareils (D-013), dans l'ordre
     * d'arrivée. S'appuie sur l'index (destinataire_id, etat).
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.destinataire.id = :destinataire AND m.etat = fr.efrei.messagerie.entity.EtatMessage.ENVOYE
            ORDER BY m.id
            """)
    List<Message> trouverNonLivres(@Param("destinataire") Long destinataire);
}
