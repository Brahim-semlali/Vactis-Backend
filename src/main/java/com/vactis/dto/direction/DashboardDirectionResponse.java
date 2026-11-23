package com.vactis.dto.direction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Synthèse Direction : 4 blocs orientés décision.
 * A. Résultat et trajectoire — B. Portefeuille à arbitrer — C. Exécution commerciale — D. Sujets à arbitrer.
 * Chaque chiffre précise son périmètre pour éviter de comparer des grandeurs différentes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDirectionResponse {

    private String mois;
    private Trajectoire trajectoire;
    private Portefeuille portefeuille;
    private Execution execution;
    private List<SujetArbitrage> sujets;

    // ── A. Résultat et trajectoire ────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Trajectoire {
        /** CA du mois, laboratoire complet (y compris non affecté). */
        private Long caMois;
        /** Cas du mois, laboratoire complet. */
        private Long casMois;
        /** CA du mois rattaché à des médecins du portefeuille. */
        private Long caPortefeuilleMois;
        /** CA du mois non rattaché à un médecin exploitable. */
        private Long caNonAffecteMois;
        /** false si aucun objectif n'est paramétré (les écarts sont alors null). */
        private Boolean objectifDefini;
        private Long objectifMois;
        /** caMois - objectifMois (null si aucun objectif). */
        private Long ecartObjectifMois;
        private Double tauxAtteinteMois;
        /** CA même mois N-1 (laboratoire complet). */
        private Long caMoisNMoins1;
        private Double evolutionMoisNMoins1Pct;
        /** Cumul depuis janvier (YTD). */
        private Long caCumule;
        private Long objectifCumuleProratise;
        private Long ecartObjectifCumule;
        private Long caCumuleNMoins1;
        private Double evolutionCumuleNMoins1Pct;
        private Long objectifAnnuel;
        /** Projection annuelle au rythme actuel (estimation, pas un engagement). */
        private Long projectionAnnuelle;
        private String statutTrajectoire;
        private List<PointMensuel> tendance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointMensuel {
        private String mois;
        private Long ca;
        private Long cas;
        private Long caNMoins1;
    }

    // ── B. Portefeuille à arbitrer ────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Portefeuille {
        /** Lignes segment A puis B, chacune découpée par situation. */
        private List<LigneSegment> segments;
        private Integer totalMedecins;
        /** CA mensuel de référence cumulé : indicateur de poids, pas une perte prévisionnelle. */
        private Long totalCaReference;
        private List<MedecinPrioritaire> prioritaires;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LigneSegment {
        private String segment;
        private CelluleSituation retention;
        private CelluleSituation silence;
        private CelluleSituation surveillance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CelluleSituation {
        private Integer nombre;
        private Long caReference;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MedecinPrioritaire {
        private Long id;
        private String nom;
        private String specialite;
        private String segment;
        /** RETENTION, SILENCE ou SURVEILLANCE. */
        private String situation;
        private Long caReference;
        private Integer joursSansActivite;
    }

    // ── C. Exécution commerciale ──────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Execution {
        /** Cycle mensuel de référence (même périmètre pour tous les chiffres ci-dessous). */
        private String cycle;
        private Long actionsDuCycle;
        private Long actionsPlanifiees;
        private Long visitesVactisRealisees;
        private Long visitesVactisNonRealisees;
        private Long actionsSansRetour;
        private Long actionsEnRetard;
        private Double tauxRealisation;
        /** Visites libres (hors actions VACTIS) : comptées à part. */
        private Long visitesLibresRealisees;
        private Long reclamationsOuvertes;
        private Long reclamationsEcheanceDepassee;
        private Long reclamationsSansEcheance;
    }

    // ── D. Sujets à arbitrer ──────────────────────────────────────────────────
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SujetArbitrage {
        /** CRITIQUE ou ATTENTION. */
        private String niveau;
        private String titre;
        private String detail;
        /** Route frontend où trouver le détail. */
        private String lien;
    }
}
