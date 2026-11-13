package com.vactis.model.medecin;

/**
 * Obstacle principal rencontré lors d'une visite commerciale défavorable (Section 5).
 * Obligatoire lorsque qualification = DEFAVORABLE.
 */
public enum ObstaclePrincipal {
    PRIX_TARIF,
    QUALITE,
    DELAI,
    RELATION,
    CONCURRENCE,
    ORGANISATION_LOGISTIQUE,
    REFUS_EXPLICITE,
    AUTRE,
    @Deprecated
    QUALITE_DELAI,
    @Deprecated
    RELATIONNEL_ACCUEIL,
}
