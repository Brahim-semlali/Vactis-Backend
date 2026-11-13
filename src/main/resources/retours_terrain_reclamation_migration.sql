BEGIN;

ALTER TABLE retours_terrain
    DROP CONSTRAINT IF EXISTS retours_terrain_qualification_check;

ALTER TABLE retours_terrain
    ADD CONSTRAINT retours_terrain_qualification_check
    CHECK (qualification IN (
        'FAVORABLE',
        'DEFAVORABLE',
        'NEUTRE',
        'RECLAMATION',
        'CONGE_ABSENCE',
        'NON_RENSEIGNE'
    ));

COMMIT;