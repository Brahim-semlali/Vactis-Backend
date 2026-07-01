-- Données de démonstration VACTIS
-- Compte admin : username=admin / password=password

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

-- Médecins de démonstration
INSERT INTO medecins (
    code_medecin, nom, prenom, specialite, organisme, ville, telephone, email,
    statut, segment, statut_pilotage, risque_urgence, ca_mois,
    date_premiere_collaboration, date_derniere_activite, commercial_referent, commentaire,
    created_at, updated_at
)
SELECT
    'MED001', 'ENNOUR', 'M''hammed', 'Chirurgie générale', 'CLINIQUE RIAD ESSALAM', 'Marrakech',
    '0612345678', 'ennour.mhammed@vactis.local', 'ACTIF', 'A', 'SURVEILLANCE', 'MOYEN', 20510,
    '2022-03-15', '2026-06-20', 'Karim Bennani', 'Médecin prioritaire surveillance.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medecins WHERE code_medecin = 'MED001');

INSERT INTO medecins (
    code_medecin, nom, prenom, specialite, organisme, ville, telephone, email,
    statut, segment, statut_pilotage, risque_urgence, ca_mois,
    date_premiere_collaboration, date_derniere_activite, commercial_referent, commentaire,
    created_at, updated_at
)
SELECT
    'MED002', 'ESSAADI', 'Abdeslam', 'Gastro-entérologie', 'Cabinet ESSAADI', 'Marrakech',
    '0623456789', 'essaadi.abdeslam@vactis.local', 'ACTIF', 'A', 'PROGRESSION', 'FAIBLE', 35150,
    '2021-08-10', '2026-06-25', 'Salma Idrissi', 'Progression CA positive.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medecins WHERE code_medecin = 'MED002');

INSERT INTO medecins (
    code_medecin, nom, prenom, specialite, organisme, ville, telephone, email,
    statut, segment, statut_pilotage, risque_urgence, ca_mois,
    date_premiere_collaboration, date_derniere_activite, commercial_referent, commentaire,
    created_at, updated_at
)
SELECT
    'MED003', 'CHU Mohamed VI de Marrakech', 'Service', 'Autre', 'CHU Med VI MARRAKECH', 'Marrakech',
    '0524334455', 'chu.m6@vactis.local', 'ACTIF', 'A', 'ACTIF', 'FAIBLE', 19520,
    '2020-01-05', '2026-06-18', 'Karim Bennani', 'Partenaire institutionnel actif.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medecins WHERE code_medecin = 'MED003');

INSERT INTO medecins (
    code_medecin, nom, prenom, specialite, organisme, ville, telephone, email,
    statut, segment, statut_pilotage, risque_urgence, ca_mois,
    date_premiere_collaboration, date_derniere_activite, commercial_referent, commentaire,
    created_at, updated_at
)
SELECT
    'MED004', 'BENALI', 'Fatima', 'Cardiologie', 'CLINIQUE INTERNATIONALE', 'Casablanca',
    '0634567890', 'benali.fatima@vactis.local', 'ACTIF', 'A', 'ONBOARDING', 'ELEVE', 12800,
    '2026-04-01', '2026-06-10', 'Salma Idrissi', 'Nouveau partenaire à qualifier.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medecins WHERE code_medecin = 'MED004');

INSERT INTO medecins (
    code_medecin, nom, prenom, specialite, organisme, ville, telephone, email,
    statut, segment, statut_pilotage, risque_urgence, ca_mois,
    date_premiere_collaboration, date_derniere_activite, commercial_referent, commentaire,
    created_at, updated_at
)
SELECT
    'MED005', 'ALAOUI', 'Hassan', 'Pédiatrie', 'HOPITAL IBN ROCHD', 'Casablanca',
    '0645678901', 'alaoui.hassan@vactis.local', 'ACTIF', 'B', 'SILENCE_CRITIQUE', 'URGENT', 8900,
    '2019-11-20', '2026-02-01', 'Karim Bennani', 'Silence critique détecté.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medecins WHERE code_medecin = 'MED005');

INSERT INTO medecins (
    code_medecin, nom, prenom, specialite, organisme, ville, telephone, email,
    statut, segment, statut_pilotage, risque_urgence, ca_mois,
    date_premiere_collaboration, date_derniere_activite, commercial_referent, commentaire,
    created_at, updated_at
)
SELECT
    'MED006', 'TAZI', 'Nadia', 'Dermatologie', 'POLYCLINIQUE LES ORANGERS', 'Rabat',
    '0656789012', 'tazi.nadia@vactis.local', 'ACTIF', 'B', 'SURVEILLANCE', 'MOYEN', 17640,
    '2021-05-12', '2026-06-22', 'Salma Idrissi', 'Segment B en surveillance.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medecins WHERE code_medecin = 'MED006');

INSERT INTO medecins (
    code_medecin, nom, prenom, specialite, organisme, ville, telephone, email,
    statut, segment, statut_pilotage, risque_urgence, ca_mois,
    date_premiere_collaboration, date_derniere_activite, commercial_referent, commentaire,
    created_at, updated_at
)
SELECT
    'MED007', 'CHRAIBI', 'Omar', 'Neurologie', 'CLINIQUE AGDAL', 'Rabat',
    '0667890123', 'chraibi.omar@vactis.local', 'ACTIF', 'A', 'PROGRESSION', 'FAIBLE', 28400,
    '2020-09-03', '2026-06-28', 'Karim Bennani', 'Fort potentiel progression.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medecins WHERE code_medecin = 'MED007');

INSERT INTO medecins (
    code_medecin, nom, prenom, specialite, organisme, ville, telephone, email,
    statut, segment, statut_pilotage, risque_urgence, ca_mois,
    date_premiere_collaboration, date_derniere_activite, commercial_referent, commentaire,
    created_at, updated_at
)
SELECT
    'MED008', 'FILALI', 'Samira', 'Gynécologie', 'CLINIQUE RIAD ESSALAM', 'Marrakech',
    '0678901234', 'filali.samira@vactis.local', 'ACTIF', 'C', 'ACTIF', 'FAIBLE', 22100,
    '2018-06-18', '2026-06-26', 'Salma Idrissi', 'Partenaire stable segment C.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medecins WHERE code_medecin = 'MED008');

INSERT INTO medecins (
    code_medecin, nom, prenom, specialite, organisme, ville, telephone, email,
    statut, segment, statut_pilotage, risque_urgence, ca_mois,
    date_premiere_collaboration, date_derniere_activite, commercial_referent, commentaire,
    created_at, updated_at
)
SELECT
    'MED009', 'BERRADA', 'Youssef', 'Ophtalmologie', 'Cabinet BERRADA', 'Fès',
    '0689012345', 'berrada.youssef@vactis.local', 'ACTIF', 'B', 'ONBOARDING', 'MOYEN', 9800,
    '2026-05-15', '2026-06-15', 'Karim Bennani', 'Onboarding en cours.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medecins WHERE code_medecin = 'MED009');

INSERT INTO medecins (
    code_medecin, nom, prenom, specialite, organisme, ville, telephone, email,
    statut, segment, statut_pilotage, risque_urgence, ca_mois,
    date_premiere_collaboration, date_derniere_activite, commercial_referent, commentaire,
    created_at, updated_at
)
SELECT
    'MED010', 'LAMRANI', 'Khadija', 'Radiologie', 'HOPITAL CHEIKH KHALIF', 'Casablanca',
    '0690123456', 'lamrani.khadija@vactis.local', 'ACTIF', 'A', 'SILENCE_CRITIQUE', 'ELEVE', 15300,
    '2019-02-22', '2026-01-30', 'Salma Idrissi', 'Relance urgente recommandée.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medecins WHERE code_medecin = 'MED010');

INSERT INTO medecins (
    code_medecin, nom, prenom, specialite, organisme, ville, telephone, email,
    statut, segment, statut_pilotage, risque_urgence, ca_mois,
    date_premiere_collaboration, date_derniere_activite, commercial_referent, commentaire,
    created_at, updated_at
)
SELECT
    'MED011', 'OUAZZANI', 'Mehdi', 'Urologie', 'CLINIQUE INTERNATIONALE', 'Casablanca',
    '0601234567', 'ouazzani.mehdi@vactis.local', 'ACTIF', 'B', 'ACTIF', 'FAIBLE', 26750,
    '2017-12-01', '2026-06-27', 'Karim Bennani', 'Bon niveau d activité.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medecins WHERE code_medecin = 'MED011');

INSERT INTO medecins (
    code_medecin, nom, prenom, specialite, organisme, ville, telephone, email,
    statut, segment, statut_pilotage, risque_urgence, ca_mois,
    date_premiere_collaboration, date_derniere_activite, commercial_referent, commentaire,
    created_at, updated_at
)
SELECT
    'MED012', 'SEKKAT', 'Laila', 'Endocrinologie', 'POLYCLINIQUE LES ORANGERS', 'Rabat',
    '0611223344', 'sekkat.laila@vactis.local', 'NOUVEAU', 'C', 'ONBOARDING', 'FAIBLE', 4200,
    '2026-06-01', '2026-06-05', 'Salma Idrissi', 'Premier contact récent.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM medecins WHERE code_medecin = 'MED012');
