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
- Docker (à partir de l'étape base de données)

Maven n'a pas besoin d'être installé : le projet embarque le wrapper `mvnw`.

## Lancement

### Serveur

```bash
cd server
./mvnw spring-boot:run
```

Le serveur écoute sur `http://localhost:8080`.

### Tests

```bash
cd server
./mvnw test
```

### Clients

À venir.

## État d'avancement

- [x] Squelette du serveur
- [ ] Modèle de données et persistance
- [ ] API REST
- [ ] Authentification JWT
- [ ] Canal WebSocket
- [ ] Clients mobile et bureau
- [ ] Fonctionnalité bonus
