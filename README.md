# API_Project_BDML_2026 : messagerie instantanée entre deux appareils

Projet de synthèse du module « API et Web Services ».

Une messagerie minimale entre deux utilisateurs, chacun sur un appareil physiquement
différent. Les appareils ne se parlent jamais directement : un serveur central reçoit chaque
message, l'enregistre en base, puis le pousse au destinataire s'il est en ligne.

- **REST** : création de compte, connexion, liste des utilisateurs, historique des messages.
- **WebSocket** : envoi et réception des messages en direct.
- **JWT** : authentification de toutes les opérations, sauf la création de compte et la connexion.

## Structure du dépôt

```
server/            serveur Spring Boot (API REST, WebSocket, sécurité)
clients/mobile/    client « mobile »   (à venir)
clients/desktop/   client « bureau »   (à venir)
```

## Prérequis

- Java 17
- Docker (base PostgreSQL et tests)

Maven n'a pas besoin d'être installé : le projet embarque le wrapper `mvnw`.

## Lancement

### 1. Configuration (une seule fois)

```bash
cp .env.example .env
```

Puis, dans `.env`, choisir un mot de passe pour la base et générer le secret de signature
des jetons :

```bash
openssl rand -base64 32   # valeur à copier dans JWT_SECRET
```

Ce fichier n'est jamais versionné ; il est lu par Docker Compose et par le serveur. Le serveur
refuse de démarrer si `JWT_SECRET` est absent ou fait moins de 256 bits.

### 2. Base de données

```bash
docker compose up -d
```

PostgreSQL démarre dans un conteneur ; ses données sont conservées dans un volume Docker
(`docker compose down` les garde, `docker compose down -v` les efface).

### 3. Serveur

```bash
cd server
./mvnw spring-boot:run
```

Le serveur crée le schéma au démarrage (`schema.sql`) et écoute sur `http://localhost:8080`.

### Tests

```bash
cd server
./mvnw test
```

Les tests démarrent leur propre PostgreSQL jetable (Testcontainers) : Docker doit être
lancé, mais la base de développement n'est jamais touchée.

### Tests manuels (Postman)

Importer `postman/messagerie.postman_collection.json` dans Postman, lancer le serveur, puis
exécuter la collection dans l'ordre. Chaque exécution crée un utilisateur au nom unique.

## API REST

| Méthode | URL | Rôle | Jeton | Codes |
|---|---|---|---|---|
| `POST` | `/api/comptes` | Créer un compte | non | 201, 400, 409 |
| `POST` | `/api/auth/login` | S'authentifier, recevoir un JWT | non | 200, 400, 401 |
| `GET` | `/api/utilisateurs` | Lister les utilisateurs | oui | 200, 401 |
| `GET` | `/api/utilisateurs/{id}` | Consulter un utilisateur | oui | 200, 401, 404 |
| `GET` | `/api/messages/{utilisateurId}` | Historique de la conversation avec cet utilisateur | oui | 200, 400, 401, 404 |

- **Authentification** : le login renvoie un JWT (HS256, valable une heure), à joindre à
  chaque requête protégée dans l'en-tête `Authorization: Bearer <jeton>`. Sans jeton valide,
  la réponse est `401`.
- **Historique** : paginé par curseur. `?limite=50` (1 à 100) renvoie les messages les plus
  récents ; `?avant=<idMessage>` remonte la conversation à partir d'un message.
- **Erreurs** : format ProblemDetail (RFC 9457, `application/problem+json`).
- **Mots de passe** : hachés avec Argon2id, jamais renvoyés.

### Clients

À venir.

## État d'avancement

- [x] Squelette du serveur
- [x] Modèle de données et persistance
- [x] API REST
- [x] Authentification JWT
- [ ] Canal WebSocket
- [ ] Clients mobile et bureau
- [ ] Fonctionnalité bonus
