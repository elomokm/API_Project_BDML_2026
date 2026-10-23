package fr.efrei.messagerie.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Message échangé entre deux utilisateurs, correspondant à la table « message ».
 */
@Entity
@Table(name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Relation vers l'expéditeur, portée par la clé étrangère expediteur_id (D-005).
     * LAZY : l'utilisateur n'est chargé depuis la base que si on y accède réellement.
     * optional = false : un message a toujours un expéditeur.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expediteur_id", nullable = false)
    private Utilisateur expediteur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destinataire_id", nullable = false)
    private Utilisateur destinataire;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;

    /**
     * Horodatage serveur dans le fuseau configuré (app.fuseau). Jamais repris d'une valeur
     * envoyée par le client.
     */
    @Column(name = "date_envoi", nullable = false)
    private LocalDateTime dateEnvoi;

    /** Stocké en texte (« ENVOYE », « LIVRE », « LU »), jamais en numéro d'ordre. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EtatMessage etat;

    /** Constructeur sans argument exigé par JPA, inutilisable ailleurs. */
    protected Message() {
    }

    /**
     * Un nouveau message est toujours à l'état ENVOYE : il vient d'être reçu par le serveur
     * et n'a encore atteint aucun appareil.
     */
    public Message(Utilisateur expediteur, Utilisateur destinataire, String contenu,
                   LocalDateTime dateEnvoi) {
        this.expediteur = expediteur;
        this.destinataire = destinataire;
        this.contenu = contenu;
        this.dateEnvoi = dateEnvoi;
        this.etat = EtatMessage.ENVOYE;
    }

    public Long getId() {
        return id;
    }

    public Utilisateur getExpediteur() {
        return expediteur;
    }

    public Utilisateur getDestinataire() {
        return destinataire;
    }

    public String getContenu() {
        return contenu;
    }

    public LocalDateTime getDateEnvoi() {
        return dateEnvoi;
    }

    public EtatMessage getEtat() {
        return etat;
    }
}
