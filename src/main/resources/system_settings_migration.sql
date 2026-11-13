-- Hibernate ddl-auto=update creates this table automatically in the current deployment.
-- Use this idempotent migration when applying schema changes manually.
CREATE TABLE IF NOT EXISTS system_settings (
    id BIGSERIAL PRIMARY KEY,
    duree_session_minutes INTEGER NOT NULL CHECK (duree_session_minutes > 0),
    duree_inactivite_jours INTEGER NOT NULL CHECK (duree_inactivite_jours > 0),
    mdp_longueur_minimale INTEGER NOT NULL DEFAULT 8 CHECK (mdp_longueur_minimale > 0),
    mdp_exige_majuscules BOOLEAN NOT NULL DEFAULT FALSE,
    mdp_exige_chiffre BOOLEAN NOT NULL DEFAULT FALSE,
    mdp_exige_caractere_special BOOLEAN NOT NULL DEFAULT FALSE,
    max_tentatives_connexion INTEGER NOT NULL DEFAULT 5 CHECK (max_tentatives_connexion > 0),
    duree_blocage_minutes INTEGER NOT NULL DEFAULT 15 CHECK (duree_blocage_minutes >= 0),
    journal_connexion_actif BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL,
    updated_by_id BIGINT REFERENCES users(id)
);

ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS duree_session_minutes INTEGER;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS duree_inactivite_jours INTEGER;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS mdp_longueur_minimale INTEGER;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS mdp_exige_majuscules BOOLEAN;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS mdp_exige_chiffre BOOLEAN;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS mdp_exige_caractere_special BOOLEAN;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS max_tentatives_connexion INTEGER;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS duree_blocage_minutes INTEGER;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS journal_connexion_actif BOOLEAN;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS bridge_goal_target BIGINT;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS bridge_goal_method VARCHAR(20) DEFAULT 'DIRECT';
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS bridge_goal_input_value NUMERIC(14, 2);
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS bridge_goal_reference_revenue BIGINT;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS laboratoire_latitude DOUBLE PRECISION;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS laboratoire_longitude DOUBLE PRECISION;

CREATE TABLE IF NOT EXISTS bridge_goal_targets (
    id BIGSERIAL PRIMARY KEY,
    period VARCHAR(7) NOT NULL UNIQUE,
    method VARCHAR(20) NOT NULL,
    input_value NUMERIC(14, 2) NOT NULL,
    previous_month_revenue BIGINT NOT NULL,
    target_amount BIGINT NOT NULL CHECK (target_amount > 0),
    updated_at TIMESTAMP NOT NULL
);

UPDATE system_settings SET duree_session_minutes = COALESCE(duree_session_minutes, 60),
    duree_inactivite_jours = COALESCE(duree_inactivite_jours, 90), mdp_longueur_minimale = COALESCE(mdp_longueur_minimale, 8),
    mdp_exige_majuscules = COALESCE(mdp_exige_majuscules, FALSE), mdp_exige_chiffre = COALESCE(mdp_exige_chiffre, FALSE),
    mdp_exige_caractere_special = COALESCE(mdp_exige_caractere_special, FALSE),
    max_tentatives_connexion = COALESCE(max_tentatives_connexion, 5), duree_blocage_minutes = COALESCE(duree_blocage_minutes, 15),
    journal_connexion_actif = COALESCE(journal_connexion_actif, TRUE);

ALTER TABLE system_settings ALTER COLUMN duree_session_minutes SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN duree_inactivite_jours SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN mdp_longueur_minimale SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN mdp_exige_majuscules SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN mdp_exige_chiffre SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN mdp_exige_caractere_special SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN max_tentatives_connexion SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN duree_blocage_minutes SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN journal_connexion_actif SET NOT NULL;

INSERT INTO system_settings (
    duree_session_minutes, duree_inactivite_jours, mdp_longueur_minimale,
    mdp_exige_majuscules, mdp_exige_chiffre, mdp_exige_caractere_special,
    max_tentatives_connexion, duree_blocage_minutes, journal_connexion_actif, updated_at
)
SELECT 60, 90, 8, FALSE, FALSE, FALSE, 5, 15, TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings);

ALTER TABLE system_settings DROP COLUMN IF EXISTS mdp_expiration_jours;

CREATE TABLE IF NOT EXISTS connexion_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    date_connexion TIMESTAMP NOT NULL,
    date_deconnexion TIMESTAMP,
    adresse_ip VARCHAR(255),
    succes BOOLEAN NOT NULL
);

-- Migration idempotente : table de notifications in-app (Sections 5 et 7 VACTIS)
-- Chaque notification est liée à un utilisateur destinataire (user_id).
-- Ne jamais stocker le mot de passe ni le hash dans cette table.
CREATE TABLE IF NOT EXISTS notifications (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    titre         VARCHAR(255) NOT NULL,
    message       VARCHAR(2000) NOT NULL,
    type          VARCHAR(50),
    reference_id  BIGINT,
    lu            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id   ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_lu        ON notifications(user_id, lu);
CREATE INDEX IF NOT EXISTS idx_notifications_created   ON notifications(created_at DESC);

CREATE TABLE IF NOT EXISTS reclamation_historique (
    id BIGSERIAL PRIMARY KEY,
    reclamation_id BIGINT NOT NULL REFERENCES reclamations(id) ON DELETE CASCADE,
    ancien_statut VARCHAR(30),
    nouveau_statut VARCHAR(30) NOT NULL,
    commentaire TEXT,
    auteur VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reclamation_historique_reclamation
    ON reclamation_historique(reclamation_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'reclamation_historique'
          AND column_name = 'date_changement'
    ) THEN
        ALTER TABLE reclamation_historique
            ALTER COLUMN date_changement SET DEFAULT CURRENT_TIMESTAMP;
    END IF;
END $$;
