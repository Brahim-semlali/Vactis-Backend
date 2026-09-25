package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO pour la Matrice de Concordance entre la qualification commerciale déclarée
 * et l'évolution chiffrée réelle observée au cycle suivant M+1 (Section 6).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcordanceQualificationResponse {

    private String mois;
    private long totalVisitesQualifiees;
    private long succesConfirmes;     // Déclaré Favorable + Réel Hausse/Stable
    private long fauxPositifs;        // Déclaré Favorable + Réel Baisse
    private long pertesConfirmees;    // Déclaré Défavorable + Réel Baisse
    private long resiliencesImprevues;// Déclaré Défavorable + Réel Hausse
    private double tauxFiabiliteCommerciale; // % de concordance globale
    private List<ConcordanceItem> details;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConcordanceItem {
        private Long retourId;
        private Long medecinId;
        private String nomMedecin;
        private String commercial;
        private String dateVisite;
        private String qualificationDeclaree;
        private String obstaclePrincipal;
        private String statutAvant;
        private String statutApres;
        private String evolutionObservee; // FAVORABLE, STABLE, DEFAVORABLE
        private String diagnosticConcordance; // SUCCES_CONFIRME, FAUX_POSITIF, ALERTE_JUSTIFIEE, RESILIENCE_INATTENDUE
        private boolean estConcordant;
    }
}
