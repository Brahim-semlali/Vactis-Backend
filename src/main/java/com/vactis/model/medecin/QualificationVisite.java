package com.vactis.model.medecin;

/**
 * Qualification du retour terrain par le visiteur.
 * Permet de catégoriser le résultat de la visite
 * (favorable, défavorable, neutre ou non renseigné).
 */
public enum QualificationVisite {
    FAVORABLE,
    DEFAVORABLE,
    NEUTRE,
    RECLAMATION,
    NON_RENSEIGNE
}
