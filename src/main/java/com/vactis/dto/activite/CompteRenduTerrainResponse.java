package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponses pour le bloc "Compte-rendu terrain du mois" (Niveau 3).
 * Contient les 4 compteurs principaux, 5 cartes cliquables, et la répartition détaillée par commercial (4 colonnes).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompteRenduTerrainResponse {

    private String mois;
    private long visitesRenseignees;
    private long visitesRealisees;
    private long visitesNonRealisees;
    private long statutNonRenseigne;
    private double tauxTerrain;

    // 5 cartes cliquables
    private long visitesAvecReclamation;
    private long defavorablesRefus;
    private long nonRealisees;
    private long statutNonRenseigneCarte;
    private long favorables;

    private List<RepartitionCommercialDetail> repartitionParCommercial;
    private List<RetourTerrainDetail> retours;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RepartitionCommercialDetail {
        private String commercial;
        private long renseignees;
        private long realisees;
        private long reclamations;
        private long favorables;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RetourTerrainDetail {
        private Long id;
        private Long medecinId;
        private String codeMedecin;
        private String nomMedecin;
        private String specialite;
        private String visiteur;
        private java.time.LocalDate dateVisite;
        private Double note;
        private String commentaire;
        private String statutVisite;
        private String qualification;
        private Boolean reclamation;
    }
}
