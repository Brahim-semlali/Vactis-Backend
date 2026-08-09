package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Réponse pour le bloc 1 du Niveau 4 : "Rapport d'impact des actions VACTIS".
 * Deux sous-sections :
 *  - Vue globale des visites terrain (toutes visites confondues, VACTIS et hors VACTIS)
 *  - Exécution des actions VACTIS (lecture sans taux de non-réalisation par commercial)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RapportImpactResponse {

    private String mois;

    // ── Vue globale des visites terrain ──────────────────────────────────────

    /** Nombre total de retours terrain enregistrés sur le mois (tout statut). */
    private long totalVisitesRenseignees;

    /** Visites avec statut_visite = REALISEE. */
    private long totalVisitesRealisees;

    /** Visites REALISEE liées à une action VACTIS (action_id IS NOT NULL). */
    private long visitesVactisRealisees;

    /** Visites REALISEE sans lien avec une action (action_id IS NULL). */
    private long visitesHorsVactisRealisees;

    /** Visites avec réclamation médecin (reclamation = TRUE). */
    private long visitesAvecReclamation;

    /** Visites avec qualification = FAVORABLE. */
    private long visitesFavorables;

    /** Visites avec qualification = DEFAVORABLE. */
    private long visitesDefavorables;

    /** Visites avec qualification = NON_RENSEIGNE ou NEUTRE. */
    private long visitesSansQualification;

    // ── Exécution des actions VACTIS ─────────────────────────────────────────

    /** Nombre total d'actions générées dans la table actions. */
    private long actionsVactisGenerees;

    /** Retours terrain liés à une action VACTIS avec statut REALISEE. */
    private long vactisRealisees;

    /** Retours terrain liés à une action VACTIS (tout statut). */
    private long vactisRenseignees;

    /** Retours terrain liés à une action VACTIS avec statut NON_REALISEE. */
    private long vactisNonRealisees;

    /** Actions sans aucun retour terrain associé : actionsGenerees - vactisRenseignees. */
    private long sanRetourTerrain;

    /** Actions dont le médecin est exclu du périmètre terrain (statut_pilotage = EXCLU). */
    private long excluesDirection;

    /**
     * Taux de réalisation (%) : vactisRealisees / (actionsGenerees - excluesDirection) × 100.
     * 0.0 si le dénominateur est 0.
     */
    private double tauxRealisation;
}
