-- Migration : participation en appui sur les visites terrain (Lecture activité → Équipe commerciale)
-- À exécuter une fois sur une base existante :
--   psql -U postgres -d vactis_db -f database/migrations/2026-11-23_retours_terrain_commercial_appui.sql
-- Le type de visite SERVICE (passage de service hors VACTIS) ne demande aucune migration :
-- type_visite est un varchar sans contrainte CHECK.
ALTER TABLE public.retours_terrain ADD COLUMN IF NOT EXISTS commercial_appui varchar(255);
