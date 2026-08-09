package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Réponse paginée pour le bloc 3 du Niveau 4 : "Détail évolution post-visite VACTIS".
 * Portée : uniquement les retours terrain liés à une action VACTIS (action_id IS NOT NULL).
 * Tableau ligne par ligne : médecin, commercial, type action/visite,
 * statut avant, qualification terrain, statut après, évolution, commentaire.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetailEvolutionResponse {

    private String mois;

    // ── Méta-pagination ──────────────────────────────────────────────────────

    private int page;
    private int taille;
    private long totalLignes;
    private int totalPages;

    // ── Lignes du tableau ─────────────────────────────────────────────────────

    private List<LigneDetail> lignes;

    /**
     * Une ligne du tableau détaillé post-visite VACTIS.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LigneDetail {

        private Long retourTerrainId;

        /** Nom complet du médecin. */
        private String nomMedecin;

        /** Commercial ayant réalisé la visite (champ visiteur de RetourTerrain). */
        private String commercial;

        /**
         * Type d'action / visite.
         * Correspond à typeVisite de RetourTerrain (AUTRE si NULL).
         * Format affiché : "visite_" + type_visite + "_" + statut_avant (libellé court).
         * Exemple : "visite_diagnostic_surveillance" → type=DIAGNOSTIC, statutAvant=surveillance.
         * Si typeVisite est NULL → affiché comme "autre".
         */
        private String typeActionVisite;

        /** Libellé court du type de visite seul (pour les filtres). */
        private String typeVisite;

        /**
         * Statut VACTIS du médecin calculé sur le mois M (mois de la visite).
         * Réutilise la logique buildStatutMapForMonth du Niveau 2.
         */
        private String statutAvant;

        /** Qualification saisie par le commercial (FAVORABLE, DEFAVORABLE, NEUTRE, NON_RENSEIGNE). */
        private String qualification;

        /**
         * Statut VACTIS du médecin calculé sur le mois M+1.
         * Null / "non_observable" si M+1 > mois courant du paramètre.
         */
        private String statutApres;

        /**
         * Évolution déduite de la comparaison des rangs.
         * Valeurs : FAVORABLE, STABLE, DEFAVORABLE, NON_OBSERVABLE.
         */
        private String evolution;

        private String commentaire;

        /** Date de la visite. */
        private LocalDate dateVisite;
    }
}
