package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Classement Top mouvements (progressions et baisses) pour une métrique (ca ou cas) entre M-1 et M
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopMouvementsResponse {

    private String mois;
    private String moisPrecedent;
    private String metrique;                      // "ca" ou "cas"
    private int limite;                           // nombre max d'éléments par liste
    private List<TopMouvementItem> progressions;  // delta positif décroissant
    private List<TopMouvementItem> baisses;       // delta négatif croissant (plus grande baisse en premier)
}
