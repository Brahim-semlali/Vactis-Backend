package com.vactis.dto;

import com.vactis.model.Medecin;
import lombok.Data;

import java.util.List;

@Data
public class MedecinPageResponse {
    private List<Medecin> items;
    private MedecinKpiResponse kpis;
    private MedecinMetaResponse meta;
    private MedecinFilterOptionsResponse filters;
}
