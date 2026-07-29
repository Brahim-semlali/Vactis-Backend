package com.vactis.dto.medecin;

import com.vactis.model.medecin.Medecin;

import lombok.Data;

import java.util.List;

@Data
public class MedecinPageResponse {
    private List<Medecin> items;
    private MedecinKpiResponse kpis;
    private MedecinMetaResponse meta;
    private MedecinFilterOptionsResponse filters;
}
