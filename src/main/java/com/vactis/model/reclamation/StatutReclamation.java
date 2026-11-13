package com.vactis.model.reclamation;

/**
 * Statut d'un ticket de réclamation (Section 7).
 */
public enum StatutReclamation {
    A_TRAITER,
    EN_COURS,
    TRAITEE,
    EN_ATTENTE_INFORMATION,
    CLOTURE_SANS_ACTION,
    /** @deprecated Legacy database status; equivalent to A_TRAITER. */
    @Deprecated
    OUVERTE,
    /** @deprecated Legacy database status; equivalent to TRAITEE. */
    @Deprecated
    RESOLUE,
    /** @deprecated Legacy value not offered by the business workflow. */
    @Deprecated
    REJETEE,
    /** @deprecated Legacy database status; equivalent to CLOTURE_SANS_ACTION. */
    @Deprecated
    CLOTUREE,
}
