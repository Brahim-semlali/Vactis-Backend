-- Données de démonstration VACTIS
-- Compte admin : username=admin / password=password

-- Supprimer les anciennes contraintes de check fixes sur statut et segment s'il y en a
ALTER TABLE IF EXISTS medecins DROP CONSTRAINT IF EXISTS medecins_statut_check;
ALTER TABLE IF EXISTS medecins DROP CONSTRAINT IF EXISTS medecins_segment_check;
ALTER TABLE IF EXISTS actions DROP CONSTRAINT IF EXISTS actions_statut_check;
ALTER TABLE IF EXISTS actions DROP CONSTRAINT IF EXISTS actions_segment_check;

-- Paramètres auth
INSERT INTO auth_settings (id, max_failed_attempts, lock_duration_minutes)
SELECT 1, 3, 2
WHERE NOT EXISTS (SELECT 1 FROM auth_settings WHERE id = 1);

-- Utilisateur admin de démonstration
INSERT INTO users (
    username,
    password,
    first_name,
    last_name,
    email,
    phone,
    role,
    enabled,
    account_locked,
    failed_login_attempts,
    created_at,
    updated_at
)
SELECT
    'admin',
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    'Admin',
    'VACTIS',
    'admin@vactis.local',
    '0600000000',
    'ADMIN',
    true,
    false,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- Corrige le mot de passe admin si deja present en base
UPDATE users
SET password = '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    enabled = true,
    account_locked = false,
    failed_login_attempts = 0,
    locked_at = NULL,
    locked_until = NULL
WHERE username = 'admin';

-- Menus sidebar (PILOTAGE)
INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Accueil', 'home', '/accueil', 1, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/accueil');

INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Dashboard Direction', 'dashboard', '/dashboard-direction', 2, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/dashboard-direction');

INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Rapport commercial', 'rapport', '/rapport-commercial', 3, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/rapport-commercial');

INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Lecture activité', 'lecture', '/lecture-activite', 4, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/lecture-activite');

INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Médecins', 'medecins', '/medecins', 5, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/medecins');

INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Actions', 'actions', '/actions', 6, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/actions');

INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Alertes hebdo', 'alertes', '/alertes-hebdo', 7, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/alertes-hebdo');

INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Recommandations', 'recommandations', '/recommandations', 8, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/recommandations');

INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Valeur détectée', 'valeur', '/valeur-detectee', 9, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/valeur-detectee');

INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Zone intelligence', 'zone', '/zone-intelligence', 10, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/zone-intelligence');

INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Qualité & doublons', 'qualite', '/qualite-doublons', 11, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/qualite-doublons');

INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Batches', 'batches', '/batches', 12, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/batches');

INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Exports terrain', 'exports', '/exports-terrain', 13, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/exports-terrain');

INSERT INTO menu_items (label, icon, route, menu_order, is_visible)
SELECT 'Statut API', 'statut', '/statut-api', 14, true
WHERE NOT EXISTS (SELECT 1 FROM menu_items WHERE route = '/statut-api');

-- Note: Les médecins et leurs actions associées ne sont plus insérés statiquement.
-- Ils sont automatiquement extraits, synchronisés et générés depuis data_fictif_test_vactis.xlsx (via DataFictifSeeder).

-- Initialisation des règles de contrôle (Controle)
INSERT INTO controle (type, etat, minca, maxca, actif)
SELECT 'STATUT', 'ACTIF', 0, 10000000, true
WHERE NOT EXISTS (SELECT 1 FROM controle WHERE type = 'STATUT' AND etat = 'ACTIF');

INSERT INTO controle (type, etat, minca, maxca, actif)
SELECT 'SEGEMENTS', 'A', 75, 100, true
WHERE NOT EXISTS (SELECT 1 FROM controle WHERE type = 'SEGEMENTS' AND etat = 'A');

INSERT INTO controle (type, etat, minca, maxca, actif)
SELECT 'SEGEMENTS', 'B', 60, 74, true
WHERE NOT EXISTS (SELECT 1 FROM controle WHERE type = 'SEGEMENTS' AND etat = 'B');

INSERT INTO controle (type, etat, minca, maxca, actif)
SELECT 'SEGEMENTS', 'C', 45, 59, true
WHERE NOT EXISTS (SELECT 1 FROM controle WHERE type = 'SEGEMENTS' AND etat = 'C');

INSERT INTO controle (type, etat, minca, maxca, actif)
SELECT 'SEGEMENTS', 'D', 0, 44, true
WHERE NOT EXISTS (SELECT 1 FROM controle WHERE type = 'SEGEMENTS' AND etat = 'D');
