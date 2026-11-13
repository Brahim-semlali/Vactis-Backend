package com.vactis.model.medecin;

/**
 * Motifs normés de non-réalisation d'une visite commerciale terrain (Section 5).
 */
public enum MotifNonRealisation {
    MEDECIN_ABSENT,
    MANQUE_DE_TEMPS,
    VISITE_REPORTEE,
    PRIORITE_MODIFIEE,
    ADRESSE_INCORRECTE_INDISPONIBLE,
    AUTRE,
    @Deprecated
    REFUS_ACCUEIL,
    @Deprecated
    SECRETARIAT_BLOQUANT,
    @Deprecated
    REPORT_DEMANDE,
    @Deprecated
    CONTRAINTE_PLANNING
}
