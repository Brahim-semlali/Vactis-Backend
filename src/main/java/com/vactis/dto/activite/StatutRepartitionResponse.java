package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Répartition des 8 statuts VACTIS pour le mois M avec compteur par statut
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatutRepartitionResponse {

    private String mois;
    private List<StatutCount> statuts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatutCount {
        private String statut;   // clé technique (ex: "progression")
        private String libelle;  // libellé affiché (ex: "Trajectoire favorable.")
        private String couleur;  // couleur CSS (ex: "green", "red")
        private long count;      // nombre de médecins dans ce statut
    }
}
