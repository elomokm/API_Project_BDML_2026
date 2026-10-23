-- Schéma de la base (D-005). Exécuté à chaque démarrage : chaque instruction est idempotente.
-- Hibernate ne crée rien : il vérifie seulement que les entités correspondent à ce schéma.

CREATE TABLE IF NOT EXISTS utilisateur (
    -- Identifiant généré par la base, jamais fourni par le client.
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- L'unicité est garantie ici, par la base : deux inscriptions simultanées de « alice »
    -- ne peuvent pas réussir toutes les deux, quel que soit le code applicatif.
    username     VARCHAR(50)  NOT NULL UNIQUE,
    -- Empreinte du mot de passe uniquement (D-007), jamais le mot de passe en clair.
    mot_de_passe VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS message (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Clés étrangères : la base refuse un message qui pointe vers un utilisateur inexistant.
    expediteur_id   BIGINT      NOT NULL REFERENCES utilisateur (id),
    destinataire_id BIGINT      NOT NULL REFERENCES utilisateur (id),
    -- Taille maximale contrôlée à la réception (D-012), pas par la colonne.
    contenu         TEXT        NOT NULL,
    -- Horodatage serveur, sans fuseau, exprimé dans le fuseau configuré (app.fuseau).
    date_envoi      TIMESTAMP   NOT NULL,
    -- État de livraison (D-013, D-016), stocké en texte pour rester lisible.
    etat            VARCHAR(10) NOT NULL DEFAULT 'ENVOYE'
                    CHECK (etat IN ('ENVOYE', 'LIVRE', 'LU'))
);

-- Historique d'une conversation trié par date : la requête lit les deux sens
-- (1 vers 2, puis 2 vers 1) et chaque sens s'appuie sur cet index.
CREATE INDEX IF NOT EXISTS idx_message_conversation
    ON message (expediteur_id, destinataire_id, date_envoi);

-- Rattrapage à la reconnexion (D-013) : messages d'un destinataire restés non livrés.
CREATE INDEX IF NOT EXISTS idx_message_destinataire_etat
    ON message (destinataire_id, etat);
