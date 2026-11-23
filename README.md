# VACTIS — Backend

API REST de VACTIS, l'outil de pilotage de la relation médecin du laboratoire : import des extractions Excel, calcul mensuel des statuts, segments et scores de chaque médecin, puis génération des actions commerciales, alertes et recommandations.

| Composant | Technologie |
|---|---|
| Langage | Java 17 |
| Framework | Spring Boot 3.4.2 (Web, Data JPA, Security, Validation, Actuator) |
| Sécurité | JWT sans état (jjwt 0.11.5), mots de passe BCrypt |
| Base de données | PostgreSQL 16 (H2 en mémoire pour les tests) |
| Import Excel | Apache POI 5.2.5 |
| Build | Maven (`./mvnw`) |
| Déploiement | Docker + Docker Compose |

La documentation technique détaillée (architecture, moteur métier, référence de l'API) est fournie à part : `VACTIS_Documentation_Backend.pdf`.

---

## Organisation du dépôt GitLab

Le code est livré **par module**, une branche `feature/<module>` par module. Chaque branche ne contient que les fichiers de son module : le projet complet s'obtient en fusionnant toutes les branches dans `main` (les fusions ne présentent aucun conflit).

| Branche | Contenu |
|---|---|
| `feature/base` | Configuration Maven, Docker, application principale, gestion des erreurs, README |
| `feature/database` | Script complet de la base (`database/vactis_schema.sql`) et migrations |
| `feature/auth` | Authentification JWT, sécurité, utilisateurs et rôles |
| `feature/menu` | Menus et sous-menus dynamiques, droits par rôle |
| `feature/system` | Paramètres système, journal de connexion, localisation du laboratoire, données initiales |
| `feature/data-excel` | Import / export des extractions Excel |
| `feature/activite` | Moteur VACTIS : statuts mensuels, scores, segmentation, impact, portefeuille |
| `feature/medecin` | Médecins, fiche contextuelle, retours terrain |
| `feature/action` | Actions commerciales et plan de rétention |
| `feature/alerte` | Alertes hebdomadaires et alerte de silence |
| `feature/recommandation` | Recommandations et moteur de commentaires |
| `feature/reclamation` | Réclamations et historique |
| `feature/bridge-to-goal` | Bridge to Goal et objectifs mensuels |
| `feature/controle` | Contrôle du chiffre d'affaires |
| `feature/concurrence` | Agences concurrentes (zone intelligence) |
| `feature/dashboard-direction` | Tableau de bord direction |
| `feature/suivi-equipe` | Suivi de l'équipe commerciale |
| `feature/notification` | Notifications |

---

## Démarrage

### Avec Docker (recommandé)

```bash
cp .env.example .env          # puis renseigner les valeurs
docker compose up --build -d  # PostgreSQL + backend
curl http://localhost:8083/actuator/health
```

Au premier démarrage (volume vide), PostgreSQL exécute `database/vactis_schema.sql` pour créer le schéma. L'API répond sur `http://localhost:8083`.

### En local

PostgreSQL doit tourner avec une base `vactis_db` créée à partir de `database/vactis_schema.sql`.

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/vactis_db
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=...
export JWT_SECRET=...              # clé Base64 d'au moins 256 bits : openssl rand -base64 32
export VACTIS_ADMIN_PASSWORD=...
./mvnw spring-boot:run
```

L'API répond sur `http://localhost:8082`.

### Variables d'environnement

| Variable | Rôle | Obligatoire |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Connexion PostgreSQL | oui (hors Docker) |
| `JWT_SECRET` | Clé de signature des jetons JWT, encodée en Base64 (256 bits minimum) | oui |
| `VACTIS_ADMIN_PASSWORD` | Mot de passe du compte `admin` créé au premier démarrage | oui |
| `JWT_EXPIRATION` | Durée par défaut d'un jeton (ms) ; la durée effective est réglée dans les paramètres système | non (24 h) |
| `CORS_ALLOWED_ORIGINS` | Origines autorisées pour le frontend | non |
| `POSTGRES_*`, `BACKEND_PORT` | Base et port exposés par Docker Compose | non |

Le fichier `.env` ne doit jamais être commité.

### Premier accès

Au premier démarrage, l'application crée le compte **`admin`** avec le mot de passe défini dans `VACTIS_ADMIN_PASSWORD`, ainsi que les rôles, menus et paramètres par défaut. La base démarre sans données métier : l'administrateur importe le classeur Excel depuis la page *Import / Export Excel* du frontend.

---

## Architecture

```
src/main/java/com/vactis/
├── config/       Sécurité (SecurityConfig, JwtAuthFilter), initialisation des données
├── controller/   21 contrôleurs REST
├── service/      Logique métier ; les règles VACTIS sont isolées dans des classes *Policy
├── repository/   Accès aux données (Spring Data JPA)
├── model/        Entités JPA
├── dto/          Objets de requête et de réponse
└── exception/    Gestion centralisée des erreurs (GlobalExceptionHandler)
```

Chemin d'une requête : `JwtAuthFilter` (vérifie le jeton) → `SecurityConfig` (droits par rôle et par menu) → contrôleur → service / policy → repository → PostgreSQL.

### Principales routes

| Préfixe | Domaine |
|---|---|
| `/api/auth` | Connexion, inscription, profil, mot de passe |
| `/api/medecins` | Médecins, fiche contextuelle, retours terrain |
| `/api/activite` | Lecture d'activité, statuts, segmentation, impact |
| `/api/actions` | Actions commerciales |
| `/api/alertes`, `/api/notifications` | Alertes hebdomadaires, notifications |
| `/api/recommandations`, `/api/reclamations` | Recommandations, réclamations |
| `/api/bridge-to-goal`, `/api/controle` | Objectifs, contrôle du CA |
| `/api/dashboard-direction`, `/api/suivi-equipe` | Pilotage direction et équipe |
| `/api/agences-concurrentes`, `/api/zone-intelligence` | Zone intelligence |
| `/api/menu`, `/api/admin/**`, `/users`, `/roles` | Menus, administration, utilisateurs, rôles |
| `/actuator/health` | État de santé (public) |

Toutes les routes, sauf la connexion, l'inscription et `/actuator/health`, exigent un jeton JWT.

---

## Tests

```bash
./mvnw test
```

215 tests (JUnit 5, Mockito, Spring Boot Test) sur une base H2 en mémoire : aucune base PostgreSQL n'est nécessaire. La CI (`.github/workflows/backend-ci.yml`) exécute `./mvnw clean verify` puis construit l'image Docker.

---

Développé dans le cadre d'un stage au laboratoire VACTIS. Usage interne.
