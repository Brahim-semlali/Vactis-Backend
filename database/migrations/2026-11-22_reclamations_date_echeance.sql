-- Migration : échéance de traitement des réclamations (Dashboard Direction / Suivi équipe)
-- À exécuter une fois sur une base existante :
--   psql -U postgres -d vactis_db -f database/migrations/2026-11-22_reclamations_date_echeance.sql
ALTER TABLE public.reclamations ADD COLUMN IF NOT EXISTS date_echeance date;
