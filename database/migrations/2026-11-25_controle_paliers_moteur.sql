-- Migration : paliers dynamiques du moteur dans la table Contrôle
-- (Rupture, Urgence, Fiabilité, Délai d'action)
-- À exécuter une fois sur une base existante :
--   psql -U postgres -d vactis_db -f database/migrations/2026-11-25_controle_paliers_moteur.sql
-- Rejouable sans risque : la colonne n'est ajoutée qu'une fois et les règles par défaut
-- ne sont insérées que pour les types encore vides (même logique que DefaultSystemDataSeeder).
-- Les valeurs reprennent à l'identique les règles VACTIS codées auparavant dans le moteur.

START TRANSACTION;

-- Délai d'action en jours (type DELAI_ACTION)
ALTER TABLE public.controle ADD COLUMN IF NOT EXISTS valeur bigint;

-- Anciennes bases créées par Hibernate : contrainte CHECK figée sur STATUT / SEGEMENTS
ALTER TABLE public.controle DROP CONSTRAINT IF EXISTS controle_type_check;

-- RUPTURE : ratio jours de silence / intervalle moyen, en % (150 = 1,5×)
INSERT INTO public.controle (type, etat, minca, maxca, actif)
SELECT v.type, v.etat, v.minca, v.maxca, true
FROM (VALUES
    ('RUPTURE', 'AUCUNE',         0, 150),
    ('RUPTURE', 'SURVEILLANCE', 150, 200),
    ('RUPTURE', 'CRITIQUE',     200, 100000)
) AS v(type, etat, minca, maxca)
WHERE NOT EXISTS (SELECT 1 FROM public.controle c WHERE c.type = 'RUPTURE');

-- URGENCE : score d'urgence finale (0-100)
INSERT INTO public.controle (type, etat, minca, maxca, actif)
SELECT v.type, v.etat, v.minca, v.maxca, true
FROM (VALUES
    ('URGENCE', 'FAIBLE',  0,  25),
    ('URGENCE', 'MOYEN',  25,  50),
    ('URGENCE', 'ELEVE',  50,  75),
    ('URGENCE', 'URGENT', 75, 100)
) AS v(type, etat, minca, maxca)
WHERE NOT EXISTS (SELECT 1 FROM public.controle c WHERE c.type = 'URGENCE');

-- FIABILITE : nombre de mois actifs (FIABLE exige aussi 6 mois d'ancienneté, règle du moteur)
INSERT INTO public.controle (type, etat, minca, maxca, actif)
SELECT v.type, v.etat, v.minca, v.maxca, true
FROM (VALUES
    ('FIABILITE', 'NON_FIABLE', 0,    2),
    ('FIABILITE', 'PARTIEL',    2,    3),
    ('FIABILITE', 'FIABLE',     3, 1200)
) AS v(type, etat, minca, maxca)
WHERE NOT EXISTS (SELECT 1 FROM public.controle c WHERE c.type = 'FIABILITE');

-- DELAI_ACTION : délai recommandé en jours selon le niveau d'intensité
INSERT INTO public.controle (type, etat, valeur, actif)
SELECT v.type, v.etat, v.valeur, true
FROM (VALUES
    ('DELAI_ACTION', 'SILENCE_STRATEGIQUE',  3),
    ('DELAI_ACTION', 'CRITIQUE',             7),
    ('DELAI_ACTION', 'ELEVEE',              15),
    ('DELAI_ACTION', 'NORMALE',             30)
) AS v(type, etat, valeur)
WHERE NOT EXISTS (SELECT 1 FROM public.controle c WHERE c.type = 'DELAI_ACTION');

COMMIT;
