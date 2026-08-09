package com.vactis.model.medecin;

/**
 * Type de visite terrain saisi par le commercial au moment du retour.
 * Utilisé pour le graphique empilé "Visites réalisées par commercial et type de visite" (Niveau 4).
 */
public enum TypeVisite {
    FIDELISATION,
    RETENTION,
    PROSPECTION,
    DIAGNOSTIC,
    RECONNAISSANCE,
    URGENCE_SILENCE,
    AUTRE
}
