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

### Scénario à deux clients (WebSocket)

Avec le serveur lancé, ce script joue le scénario de l'énoncé avec deux clients simultanés
(création des comptes, connexion, échange de messages, déconnexion, rattrapage, historique) :

```bash
node scripts/scenario-deux-clients.mjs
```

Node 22 ou plus, aucune dépendance.

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

## Canal temps réel (WebSocket)

Adresse : `ws://localhost:8080/ws/messages`. Le jeton obtenu au login est transmis pendant le
handshake, dans le sous-protocole (jamais dans l'URL) :

```javascript
const canal = new WebSocket("ws://localhost:8080/ws/messages", ["messagerie.v1", jeton]);
```

Sans jeton valide, le handshake est refusé (`401`). Tous les messages sont en JSON et portent
un champ `type`.

| Sens | `type` | Champs |
|---|---|---|
| client vers serveur | `message` | `destinataireId`, `contenu` |
| client vers serveur | `accuse` | `messageId` (accusé de réception automatique) |
| serveur vers client | `message` | `id`, `expediteurId`, `destinataireId`, `contenu`, `dateEnvoi`, `etat` |
| serveur vers client | `presence` | `utilisateurId`, `connecte` |
| serveur vers client | `erreur` | `code`, `message` |

À la réception d'un message, le serveur le valide, l'enregistre, puis le pousse au
destinataire s'il est connecté. L'expéditeur est toujours l'utilisateur du jeton et la date
celle du serveur. Un destinataire hors ligne reçoit ses messages en attente dès sa
reconnexion.

### Clients

```bash
node scripts/serveur-clients.mjs
```

Le script sert les deux clients sur le port 8081 et affiche les adresses à ouvrir :

- **client bureau** : `http://localhost:8081/desktop/` sur l'ordinateur ;
- **client mobile** : `http://<adresse IP de l'ordinateur>:8081/mobile/` sur le téléphone,
  connecté au même Wi-Fi.

Si le Wi-Fi isole les appareils entre eux (fréquent sur les réseaux d'école ou
d'entreprise), activer le partage de connexion du téléphone et y connecter l'ordinateur.
Le pare-feu de l'ordinateur doit autoriser les connexions entrantes vers Java et Node.

Les origines autorisées à appeler l'API (CORS) et à ouvrir le canal sont, par défaut, le
port 8081 en local et sur les adresses privées (`192.168.*`, `10.*`, `172.*`). Elles se
règlent avec la variable `APP_ORIGINES`.

## Scénario de démonstration

1. `docker compose up -d`, lancer le serveur, puis `node scripts/serveur-clients.mjs`.
2. Ordinateur : ouvrir le client bureau, créer ou connecter l'utilisateur 1.
3. Téléphone : ouvrir le client mobile, créer ou connecter l'utilisateur 2.
4. Envoyer un message de chaque côté : il apparaît immédiatement sur l'autre appareil.
5. Se déconnecter sur l'ordinateur, puis se reconnecter : l'historique complet est visible.

Pour repartir d'une base vide avant la démonstration : `docker compose down -v`, puis
`docker compose up -d`.

## État d'avancement

- [x] Squelette du serveur
- [x] Modèle de données et persistance
- [x] API REST
- [x] Authentification JWT
- [x] Canal WebSocket
- [x] Clients mobile et bureau
- [ ] Fonctionnalité bonus
