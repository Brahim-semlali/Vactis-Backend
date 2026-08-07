package com.vactis.model.medecin;

/**
 * Statut d'exécution d'une visite terrain.
 * Permet de distinguer une visite effectivement réalisée
 * d'une visite planifiée mais non exécutée.
 */
public enum StatutVisite {
    REALISEE,
    NON_REALISEE,
    NON_RENSEIGNE
}
