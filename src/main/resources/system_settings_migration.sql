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
    mdp_expiration_jours INTEGER NOT NULL DEFAULT 0 CHECK (mdp_expiration_jours >= 0),
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
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS mdp_expiration_jours INTEGER;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS max_tentatives_connexion INTEGER;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS duree_blocage_minutes INTEGER;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS journal_connexion_actif BOOLEAN;

UPDATE system_settings SET duree_session_minutes = COALESCE(duree_session_minutes, 60),
    duree_inactivite_jours = COALESCE(duree_inactivite_jours, 90), mdp_longueur_minimale = COALESCE(mdp_longueur_minimale, 8),
    mdp_exige_majuscules = COALESCE(mdp_exige_majuscules, FALSE), mdp_exige_chiffre = COALESCE(mdp_exige_chiffre, FALSE),
    mdp_exige_caractere_special = COALESCE(mdp_exige_caractere_special, FALSE), mdp_expiration_jours = COALESCE(mdp_expiration_jours, 0),
    max_tentatives_connexion = COALESCE(max_tentatives_connexion, 5), duree_blocage_minutes = COALESCE(duree_blocage_minutes, 15),
    journal_connexion_actif = COALESCE(journal_connexion_actif, TRUE);

ALTER TABLE system_settings ALTER COLUMN duree_session_minutes SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN duree_inactivite_jours SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN mdp_longueur_minimale SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN mdp_exige_majuscules SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN mdp_exige_chiffre SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN mdp_exige_caractere_special SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN mdp_expiration_jours SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN max_tentatives_connexion SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN duree_blocage_minutes SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN journal_connexion_actif SET NOT NULL;

INSERT INTO system_settings (duree_session_minutes, duree_inactivite_jours, updated_at)
SELECT 60, 90, 8, FALSE, FALSE, FALSE, 0, 5, 15, TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings);

CREATE TABLE IF NOT EXISTS connexion_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    date_connexion TIMESTAMP NOT NULL,
    date_deconnexion TIMESTAMP,
    adresse_ip VARCHAR(255),
    succes BOOLEAN NOT NULL
);