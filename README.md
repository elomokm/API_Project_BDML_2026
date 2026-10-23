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

Puis choisir un mot de passe dans `.env`. Ce fichier n'est jamais versionné ; il est lu par
Docker Compose et par le serveur.

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

### Clients

À venir.

## État d'avancement

- [x] Squelette du serveur
- [x] Modèle de données et persistance
- [ ] API REST
- [ ] Authentification JWT
- [ ] Canal WebSocket
- [ ] Clients mobile et bureau
- [ ] Fonctionnalité bonus
