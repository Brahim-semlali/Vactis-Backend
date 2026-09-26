package com.vactis.model.medecin;

/**
 * Obstacle principal rencontré lors d'une visite commerciale défavorable (Section 5).
 * Obligatoire lorsque qualification = DEFAVORABLE.
 */
public enum ObstaclePrincipal {
    PRIX_TARIF,
    QUALITE_DELAI,
    RELATIONNEL_ACCUEIL,
    CONCURRENCE,
    AUTRE
}
