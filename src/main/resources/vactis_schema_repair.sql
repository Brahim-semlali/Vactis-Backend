-- 1. Table menu_principal
CREATE TABLE IF NOT EXISTS menu_principal (
    id_menu_princ BIGSERIAL PRIMARY KEY,
    nom VARCHAR(255) NOT NULL,
    icone VARCHAR(100),
    menu_order INTEGER NOT NULL DEFAULT 0
);

INSERT INTO menu_principal (nom, icone, menu_order)
SELECT seed.nom, seed.icone, seed.menu_order
FROM (VALUES
    ('Pilotage', 'dashboard', 1),
    ('Portefeuille médecins', 'stethoscope', 2),
    ('Terrain & Actions', 'clipboard', 3),
    ('Qualité des données', 'layers', 4),
    ('Administration', 'roles', 5)
) AS seed(nom, icone, menu_order)
WHERE NOT EXISTS (SELECT 1 FROM menu_principal existing WHERE existing.nom = seed.nom);

-- 2. Colonne menu_principal sur menu_items
ALTER TABLE menu_items
    ADD COLUMN IF NOT EXISTS id_menu_princ BIGINT
    REFERENCES menu_principal(id_menu_princ);

-- 3. Table roles et role_menu
CREATE TABLE IF NOT EXISTS roles (
    id_role BIGSERIAL PRIMARY KEY,
    name_role VARCHAR(255),
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS role_menu (
    id_role BIGINT REFERENCES roles(id_role),
    id_menu BIGINT REFERENCES menu_items(id_menu),
    PRIMARY KEY (id_role, id_menu)
);

-- Colonne id_role dans users
ALTER TABLE users ADD COLUMN IF NOT EXISTS id_role BIGINT REFERENCES roles(id_role);
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

-- Insérer les rôles de base si absents
INSERT INTO roles (name_role, description)
SELECT 'ADMIN', 'Administrateur du système'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name_role = 'ADMIN');

INSERT INTO roles (name_role, description)
SELECT 'USER', 'Utilisateur standard'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name_role = 'USER');

INSERT INTO roles (name_role, description)
SELECT 'COMMERCIALE', 'Commerciale du système'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name_role = 'COMMERCIALE');

-- 4. Colonnes et contraintes sur medecins
ALTER TABLE medecins
    ADD COLUMN IF NOT EXISTS is_a_reactiver_manuel BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS is_profil_irregulier BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS score_urgence DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS intensite_risque DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS tendance_risque DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS note_input DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS ca_mensuel_moyen DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS poids_eco_sur100 DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS variation_mixte_sur100 DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS reference_ca DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS reference_volume DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS variation_ca DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS variation_volume DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS jours_sans_activite INTEGER,
    ADD COLUMN IF NOT EXISTS baisse_reference DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS baisse_courte DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS fiabilite VARCHAR(50),
    ADD COLUMN IF NOT EXISTS intervalle_effectif INTEGER,
    ADD COLUMN IF NOT EXISTS score_silence DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS score_risque DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS ca_baseline INTEGER,
    ADD COLUMN IF NOT EXISTS ca_total INTEGER,
    ADD COLUMN IF NOT EXISTS total_cas INTEGER,
    ADD COLUMN IF NOT EXISTS potentiel_sur100 DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS performance_sur100 DOUBLE PRECISION;

-- Mettre à jour les booléens nulls et migrer les statuts obsolètes
UPDATE medecins SET is_a_reactiver_manuel = false WHERE is_a_reactiver_manuel IS NULL;
UPDATE medecins SET is_profil_irregulier = false WHERE is_profil_irregulier IS NULL;
UPDATE medecins SET statut_pilotage = 'RETENTION' WHERE statut_pilotage = 'SILENCE_CRITIQUE';
UPDATE medecins SET statut_pilotage = 'INACTIF' WHERE statut_pilotage = 'EXCLU';

-- Supprimer les contraintes de vérification restrictives qui empêchent les nouveaux statuts VACTIS
ALTER TABLE medecins DROP CONSTRAINT IF EXISTS medecins_statut_pilotage_check;
ALTER TABLE medecins DROP CONSTRAINT IF EXISTS medecins_statut_check;
ALTER TABLE medecins DROP CONSTRAINT IF EXISTS medecins_segment_check;
ALTER TABLE medecins DROP CONSTRAINT IF EXISTS medecins_risque_urgence_check;

-- 5. Colonnes et contraintes sur actions
ALTER TABLE actions
    ADD COLUMN IF NOT EXISTS reserved_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reserved_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS is_reserved BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS motif_non_realisation TEXT,
    ADD COLUMN IF NOT EXISTS qualification VARCHAR(50),
    ADD COLUMN IF NOT EXISTS obstacle_principal VARCHAR(30),
    ADD COLUMN IF NOT EXISTS prochaine_action VARCHAR(255),
    ADD COLUMN IF NOT EXISTS date_prochaine_action DATE,
    ADD COLUMN IF NOT EXISTS action_parente_id BIGINT,
    ADD COLUMN IF NOT EXISTS statut_plan_retention VARCHAR(30),
    ADD COLUMN IF NOT EXISTS type_etape_retention VARCHAR(30) DEFAULT 'AUCUNE',
    ADD COLUMN IF NOT EXISTS horizon_jours INTEGER,
    ADD COLUMN IF NOT EXISTS date_echeance DATE,
    ADD COLUMN IF NOT EXISTS est_en_retard BOOLEAN DEFAULT false;

UPDATE actions SET is_reserved = false WHERE is_reserved IS NULL;
UPDATE actions SET est_en_retard = false WHERE est_en_retard IS NULL;
UPDATE actions SET type_etape_retention = 'AUCUNE' WHERE type_etape_retention IS NULL;

ALTER TABLE actions DROP CONSTRAINT IF EXISTS actions_statut_check;
ALTER TABLE actions DROP CONSTRAINT IF EXISTS actions_segment_check;
ALTER TABLE actions DROP CONSTRAINT IF EXISTS actions_urgence_check;
ALTER TABLE actions DROP CONSTRAINT IF EXISTS actions_etat_action_check;

-- 6. Table retours_terrain
CREATE TABLE IF NOT EXISTS retours_terrain (
    id BIGSERIAL PRIMARY KEY,
    medecin_id BIGINT NOT NULL REFERENCES medecins(id),
    nom_medecin VARCHAR(255),
    note DOUBLE PRECISION,
    date_visite DATE NOT NULL,
    commentaire TEXT,
    motif_non_realisation TEXT,
    prochaine_action VARCHAR(255),
    date_prochaine_action DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    qualification VARCHAR(50),
    action_id BIGINT REFERENCES actions(id),
    statut_apres_visite VARCHAR(30),
    obstacle_principal VARCHAR(30)
);

-- 7. Table reclamations (Section 7)
CREATE TABLE IF NOT EXISTS reclamations (
    id BIGSERIAL PRIMARY KEY,
    medecin_id BIGINT NOT NULL REFERENCES medecins(id),
    commercial VARCHAR(255),
    date_declaration DATE NOT NULL,
    categorie VARCHAR(50) NOT NULL,
    priorite VARCHAR(20) NOT NULL,
    statut VARCHAR(30) NOT NULL,
    description TEXT,
    responsable VARCHAR(255),
    solution_apportee TEXT,
    date_resolution DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 8. Table alertes_hebdomadaires (Section 10)
CREATE TABLE IF NOT EXISTS alertes_hebdomadaires (
    id BIGSERIAL PRIMARY KEY,
    medecin_id BIGINT NOT NULL REFERENCES medecins(id),
    date_detection DATE NOT NULL,
    jours_silence INTEGER,
    intervalle_moyen DOUBLE PRECISION,
    score_urgence_silence DOUBLE PRECISION,
    type_alerte VARCHAR(30),
    statut VARCHAR(30) NOT NULL,
    action_id BIGINT REFERENCES actions(id),
    commentaire TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. Tables system_settings et connexion_log
CREATE TABLE IF NOT EXISTS system_settings (
    id BIGSERIAL PRIMARY KEY,
    cle VARCHAR(100) NOT NULL UNIQUE,
    valeur TEXT,
    description TEXT,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS connexion_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    date_connexion TIMESTAMP NOT NULL,
    date_deconnexion TIMESTAMP,
    adresse_ip VARCHAR(255),
    succes BOOLEAN NOT NULL
);

