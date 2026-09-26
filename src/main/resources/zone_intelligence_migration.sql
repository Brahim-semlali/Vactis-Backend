-- ==============================================================================
-- Migration SQL idempotente pour le module Zone Intelligence V1
-- ==============================================================================

-- 1. Ajout des colonnes de coordonnées géographiques sur la table medecins
ALTER TABLE medecins ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE medecins ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;

-- 2. Création de la table des agences concurrentes
CREATE TABLE IF NOT EXISTS agences_concurrentes (
    id BIGSERIAL PRIMARY KEY,
    nom VARCHAR(255) NOT NULL,
    enseigne VARCHAR(255),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    adresse VARCHAR(500),
    notes TEXT,
    created_at TIMESTAMP NOT NULL,
    created_by_id BIGINT REFERENCES users(id),
    updated_at TIMESTAMP NOT NULL,
    updated_by_id BIGINT REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_agences_coords ON agences_concurrentes(latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_medecins_coords ON medecins(latitude, longitude);
