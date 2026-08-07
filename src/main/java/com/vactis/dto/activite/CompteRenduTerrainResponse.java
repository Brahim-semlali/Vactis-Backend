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
}
