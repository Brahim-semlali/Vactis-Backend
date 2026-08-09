package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Réponse pour le bloc 2 du Niveau 4.
 * Contient deux sous-sections :
 *  - "Visites réalisées par commercial et type de visite" : graphique empilé
 *  - "Évolution observée après visites VACTIS" : classification globale + par commercial
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvolutionParCommercialResponse {

    private String mois;

    // ── Graphique empilé par commercial ──────────────────────────────────────

    /** Liste des commerciaux avec détail par type de visite. */
    private List<RepartitionCommercial> visitesParCommercial;

    // ── Classification globale (Évolution observée, toutes VACTIS analysées) ─

    private long totalAnalyse;
    private long favorable;
    private long stable;
    private long defavorable;
    private long nonObservable;

    // ── Classification par commercial ────────────────────────────────────────

    private List<EvolutionCommercial> evolutionParCommercial;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Répartition des visites réalisées d'un commercial par type de visite.
     * Les clés de la map typeVisite sont les libellés enum (FIDELISATION, RETENTION, etc.).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RepartitionCommercial {
        private String commercial;
        /** Nombre total de visites réalisées pour ce commercial. */
        private long totalRealisees;
        /**
         * Ventilation par type de visite.
         * Clé = valeur de l'enum TypeVisite (ex. "FIDELISATION"), valeur = nombre de visites.
         */
        private Map<String, Long> parTypeVisite;
    }

    /**
     * Évolution post-visite VACTIS d'un commercial.
     * Portée : uniquement les visites VACTIS (action_id IS NOT NULL) dont le statut M+1 est calculable.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EvolutionCommercial {
        private String commercial;
        /** Nombre de visites VACTIS analysées pour ce commercial. */
        private long totalAnalyse;
        private long favorable;
        private long stable;
        private long defavorable;
        private long nonObservable;
        /**
         * Taux favorable (%) : favorable / (totalAnalyse - nonObservable) × 100.
         * Représente la part des évolutions mesurables qui sont favorables.
         * 0.0 si aucune évolution mesurable.
         */
        private double tauxFavorable;
    }
}
