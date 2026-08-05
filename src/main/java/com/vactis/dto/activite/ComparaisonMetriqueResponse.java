package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComparaisonMetriqueResponse {
    private Double moisCourant;
    private Double moisPrecedent;
    private Double referenceRecente;
    private Double variationVsMPrecedentVal;
    private Double variationVsMPrecedentPct;
    private Double variationVsRefVal;
    private Double variationVsRefPct;
}
