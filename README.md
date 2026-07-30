# 🏥 Vactis Backend

> API REST Spring Boot pour la gestion de la relation médecin — Laboratoire VACTIS

[![CI Backend](https://github.com/YOUR_ORG/Vactis-Backend/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/YOUR_ORG/Vactis-Backend/actions/workflows/backend-ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.6-green?logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker)

---

## 📋 Description

**Vactis Backend** est l'API REST du système de pilotage de la relation médecin développé pour le laboratoire **VACTIS**. Il expose les endpoints nécessaires à la gestion des médecins, des actions commerciales, du suivi des dossiers patients et du contrôle des règles métier (statuts, segments CA).

### Fonctionnalités principales

- 🔐 **Authentification JWT** — Login sécurisé avec blocage de compte après tentatives échouées
- 👨‍⚕️ **Gestion des médecins** — CRUD complet avec statut, segment CA, pilotage et risque urgence
- 📊 **Actions commerciales** — Suivi des visites, relances et états planifiés/réalisés
- 📁 **Import Excel** — Synchronisation automatique depuis `data_fictif_test_vactis.xlsx`
- ⚙️ **Règles de contrôle dynamiques** — Paramétrage des seuils CA par type (STATUT / SEGMENT)
- 🩺 **Actuator Health** — Endpoint `/actuator/health` pour la supervision
- 📖 **Swagger UI** — Documentation API interactive disponible à `/swagger-ui.html`

---

## 🛠️ Stack technique

| Composant       | Technologie                 |
|-----------------|-----------------------------|
| Langage         | Java 17                     |
| Framework       | Spring Boot 4.0.6           |
| Sécurité        | Spring Security + JWT (JJWT 0.11.5) |
| Persistance     | Spring Data JPA + Hibernate |
| Base de données | PostgreSQL 16               |
| Import Excel    | Apache POI 5.2.5            |
| Build           | Maven (mvnw)                |
| Conteneurisation| Docker + Docker Compose     |
| CI/CD           | GitHub Actions              |
| Documentation   | SpringDoc OpenAPI (Swagger) |

---

## 🚀 Démarrage rapide

### Prérequis

- Java 17+
- Maven 3.9+ (ou utiliser `./mvnw`)
- PostgreSQL 16 (ou Docker)
- Docker & Docker Compose (recommandé)

---

### ▶️ Avec Docker Compose (recommandé)

```bash
# 1. Cloner le dépôt
git clone https://github.com/YOUR_ORG/Vactis-Backend.git
cd Vactis-Backend

# 2. Configurer les variables d'environnement
cp .env.example .env
# Modifier .env avec vos valeurs

# 3. Lancer PostgreSQL + Backend
docker compose up --build -d

# 4. Vérifier que l'API est disponible
curl http://localhost:8083/actuator/health
```

L'API sera disponible sur : **`http://localhost:8083`**

---

### ▶️ En local (sans Docker)

```bash
# 1. S'assurer que PostgreSQL tourne sur localhost:5432
#    avec une base vactis_db créée

# 2. Définir les variables d'environnement
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/vactis_db
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=votre_mdp
export JWT_SECRET=votre_secret_jwt

# 3. Lancer l'application
./mvnw spring-boot:run
```

L'API sera disponible sur : **`http://localhost:8082`**

---

## ⚙️ Variables d'environnement

Copier `.env.example` vers `.env` et renseigner les valeurs :

| Variable                    | Description                              | Défaut                        |
|-----------------------------|------------------------------------------|-------------------------------|
| `SPRING_DATASOURCE_URL`     | URL JDBC de connexion PostgreSQL         | `jdbc:postgresql://localhost:5432/vactis_db` |
| `SPRING_DATASOURCE_USERNAME`| Utilisateur PostgreSQL                   | `postgres`                    |
| `SPRING_DATASOURCE_PASSWORD`| Mot de passe PostgreSQL                  | *(vide)*                      |
| `JWT_SECRET`                | Clé secrète pour signer les tokens JWT   | *(valeur de dev uniquement)*  |
| `JWT_EXPIRATION`            | Durée de validité du token en ms         | `86400000` (24h)              |
| `CORS_ALLOWED_ORIGINS`      | Origines autorisées pour CORS            | `http://localhost:5173`       |
| `POSTGRES_PORT`             | Port exposé de PostgreSQL (Docker)       | `5432`                        |
| `BACKEND_PORT`              | Port exposé du backend (Docker)          | `8083`                        |

> ⚠️ **Ne jamais committer le fichier `.env`** — il est dans `.gitignore`.

---

## 📡 Endpoints API principaux

| Méthode | Endpoint                        | Description                          | Auth requise |
|---------|---------------------------------|--------------------------------------|:---:|
| POST    | `/api/auth/login`               | Connexion, retourne un token JWT     | ❌  |
| POST    | `/api/auth/register`            | Créer un compte utilisateur          | ❌  |
| GET     | `/api/medecins`                 | Lister tous les médecins             | ✅  |
| POST    | `/api/medecins`                 | Créer un médecin                     | ✅  |
| PUT     | `/api/medecins/{id}`            | Modifier un médecin                  | ✅  |
| DELETE  | `/api/medecins/{id}`            | Supprimer un médecin                 | ✅  |
| POST    | `/api/medecins/sync-excel`      | Synchroniser depuis le fichier Excel | ✅  |
| GET     | `/api/actions`                  | Lister les actions commerciales      | ✅  |
| GET     | `/api/controles`                | Lister les règles de contrôle        | ✅  |
| POST    | `/api/controles`                | Créer une règle de contrôle          | ✅  |
| GET     | `/actuator/health`              | Status de santé de l'application     | ❌  |

📖 **Documentation complète** : [`http://localhost:8082/swagger-ui.html`](http://localhost:8082/swagger-ui.html)

---

## 🧪 Tests

```bash
# Lancer les tests unitaires et d'intégration
./mvnw test

# Ou avec rapport complet
./mvnw clean verify
```

> Les tests d'intégration nécessitent une base PostgreSQL accessible. Voir le workflow CI pour la configuration avec un conteneur PostgreSQL.

---

## 🐳 Docker

```bash
# Construire l'image Docker
docker build -t vactis-backend:latest .

# Lancer l'image seule (PostgreSQL déjà disponible)
docker run -p 8082:8082 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/vactis_db \
  -e SPRING_DATASOURCE_PASSWORD=votre_mdp \
  -e JWT_SECRET=votre_secret \
  vactis-backend:latest
```

---

## ⚡ CI/CD — GitHub Actions

Le pipeline CI (`.github/workflows/backend-ci.yml`) s'exécute automatiquement sur chaque **push** et **pull request** vers `main` :

1. ✅ Démarrage d'un conteneur **PostgreSQL 15** de test
2. ✅ Installation **JDK 17** (Temurin) avec cache Maven
3. ✅ **Build & Tests** — `./mvnw clean verify`
4. ✅ **Build Docker Image** — Vérifie que l'image se construit correctement

---

## 🗂️ Structure du projet

```
src/
├── main/
│   ├── java/com/vactis/
│   │   ├── config/          # Sécurité, JWT, CORS, seeding
│   │   ├── controller/      # AuthController, MedecinController, ActionController, ControleController
│   │   ├── dto/             # DTOs requêtes/réponses
│   │   ├── exception/       # GlobalExceptionHandler
│   │   ├── model/           # Entités JPA (Medecin, Action, Controle, User...)
│   │   ├── repository/      # Spring Data JPA repositories
│   │   └── service/         # Logique métier + ExcelImportService
│   └── resources/
│       ├── application.properties
│       ├── data.sql          # Données initiales (admin, paramètres)
│       └── data/             # Fichier Excel fictif pour les tests
└── test/
    └── java/com/vactis/      # Tests Spring Boot
```

---

## 👤 Identifiants de démonstration

| Champ      | Valeur          |
|------------|-----------------|
| Username   | `admin`         |
| Password   | `password`      |
| Rôle       | `ADMIN`         |

---

## 👥 Auteurs

Développé dans le cadre d'un stage au laboratoire **VACTIS** — Marrakech.

---

## 📄 Licence

Usage interne — © VACTIS. Tous droits réservés.
