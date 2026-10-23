package fr.efrei.messagerie.entity;

/**
 * État de livraison d'un message (D-013, D-016).
 * Un message ne fait qu'avancer : ENVOYE, puis LIVRE, puis LU, jamais en arrière.
 * Stocké en base sous forme de texte (EnumType.STRING) : l'ordre des constantes ci-dessous
 * peut changer sans altérer le sens des données déjà enregistrées.
 */
public enum EtatMessage {
    /** Reçu et enregistré par le serveur. */
    ENVOYE,
    /** Arrivé sur l'appareil du destinataire (accusé automatique de son client). */
    LIVRE,
    /** Vu par le destinataire, conversation ouverte et page visible. */
    LU
}
