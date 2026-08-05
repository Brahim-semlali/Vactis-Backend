package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComparaisonResponse {
    private String mois;
    private String moisPrecedent;
    private Integer fenetreRefMois;
    private ComparaisonMetriqueResponse cas;
    private ComparaisonMetriqueResponse ca;
}
