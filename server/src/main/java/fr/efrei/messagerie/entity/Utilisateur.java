package fr.efrei.messagerie.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Compte utilisateur, correspondant à la table « utilisateur » de schema.sql.
 * Cette entité n'est jamais renvoyée telle quelle en JSON : elle contient l'empreinte du
 * mot de passe. Les réponses de l'API passeront par des DTO (D-006).
 */
@Entity
@Table(name = "utilisateur")
public class Utilisateur {

    /** Généré par la base (colonne IDENTITY) au moment de l'insertion. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** Empreinte du mot de passe (hachage décidé en D-007), jamais le mot de passe en clair. */
    @Column(name = "mot_de_passe", nullable = false)
    private String motDePasse;

    /** Constructeur sans argument exigé par JPA, inutilisable ailleurs. */
    protected Utilisateur() {
    }

    public Utilisateur(String username, String motDePasse) {
        this.username = username;
        this.motDePasse = motDePasse;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getMotDePasse() {
        return motDePasse;
    }
}
