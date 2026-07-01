package com.vactis.dto;

import lombok.Data;

@Data
public class MedecinKpiResponse {
    private Long total;
    private Long segmentsAB;
    private Long surveillance;
    private Long onboarding;
    private Long silenceCritique;
    private Long actionsEnCours;
}
