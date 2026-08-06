package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Liste complète des flux de transitions (statut M-1 → statut M), triée par effectif décroissant
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FluxAgregesResponse {

    private String mois;
    private int totalFlux;           // nombre de paires de transitions distinctes
    private List<FluxAgregeItem> flux;
}
