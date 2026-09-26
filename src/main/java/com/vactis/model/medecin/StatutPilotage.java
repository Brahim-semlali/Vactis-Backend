package com.vactis.model.medecin;


public enum StatutPilotage {
    PROGRESSION,
    ACTIF_STABLE,
    SURVEILLANCE,
    RETENTION,
    ONBOARDING,
    A_REACTIVER,
    INACTIF,
    ACTIF,
    /** @deprecated Ancien statut pré-VACTIS7. Remplacé par RETENTION. Maintenu pour compatibilité ascendante avec les données existantes. */
    @Deprecated
    SILENCE_CRITIQUE,
    /** @deprecated Ancien statut pré-VACTIS7. Remplacé par INACTIF. */
    @Deprecated
    EXCLU
}
