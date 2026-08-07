package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponses pour le bloc "Lecture réalisation commerciale / actions VACTIS" (Niveau 3).
 * Contient les 5 compteurs globaux du mois et la répartition par commercial (3 colonnes).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionsVactisResponse {

    private String mois;
    private long actionsGenerees;
    private long visitesRenseignees;
    private long visitesRealisees;
    private long nonRealisees;
    private double tauxTerrain;

    private List<RepartitionCommercial> repartitionParCommercial;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RepartitionCommercial {
        private String commercial;
        private long renseignees;
        private long realisees;
        private long nonRealisees;
    }
}
