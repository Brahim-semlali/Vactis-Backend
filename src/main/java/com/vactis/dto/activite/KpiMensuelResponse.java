package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiMensuelResponse {
    private String mois;
    private Long caMoisTotal;
    private Long casMoisTotal;
    private Long medecinsAvecActivite;
    private Long portefeuilleMedecins;
    private Long portefeuilleCA;
    private Long nonAffectesCount;
    private Double nonAffectesPct;
    private Long nonAffectesCA;
}
