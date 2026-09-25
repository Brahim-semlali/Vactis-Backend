package com.vactis.dto.recommandation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommandationsGlobalResponse {
    private List<RecommandationItemResponse> developpement;
    private List<RecommandationItemResponse> reactivation;
    private List<RecommandationItemResponse> irreguliersRentables;
    private long totalOpportunites;
}
