CREATE TABLE IF NOT EXISTS menu_principal (
    id_menu_princ BIGSERIAL PRIMARY KEY,
    nom VARCHAR(255) NOT NULL,
    icone VARCHAR(100),
    menu_order INTEGER NOT NULL DEFAULT 0
);

ALTER TABLE menu_items
    ADD COLUMN IF NOT EXISTS id_menu_princ BIGINT
    REFERENCES menu_principal(id_menu_princ);

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

UPDATE menu_items item
SET id_menu_princ = principal.id_menu_princ
FROM menu_principal principal
WHERE principal.nom = 'Pilotage'
  AND item.label IN ('Accueil', 'Dashboard Direction', 'Rapport commercial', 'Lecture activité');

UPDATE menu_items item
SET id_menu_princ = principal.id_menu_princ
FROM menu_principal principal
WHERE principal.nom = 'Portefeuille médecins'
  AND item.label IN ('Médecins', 'Valeur détectée', 'Zone intelligence');

UPDATE menu_items item
SET id_menu_princ = principal.id_menu_princ
FROM menu_principal principal
WHERE principal.nom = 'Terrain & Actions'
  AND item.label IN ('Actions', 'Alertes hebdo', 'Recommandations');

UPDATE menu_items item
SET id_menu_princ = principal.id_menu_princ
FROM menu_principal principal
WHERE principal.nom = 'Qualité des données'
  AND item.label IN ('Qualité & doublons', 'Batches', 'Statut API');

UPDATE menu_items item
SET id_menu_princ = principal.id_menu_princ
FROM menu_principal principal
WHERE principal.nom = 'Administration'
  AND item.label IN ('Contrôle', 'Rôles', 'Users', 'Exports terrain');