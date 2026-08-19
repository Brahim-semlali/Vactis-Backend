package com.vactis.dto.medecin;

import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RetourTerrain;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class FicheContextuelleResponse {

    private Medecin medecin;
    private String statutExplanation;
    private String silenceRadioStatus;
    private Integer joursSansActivite;
    private Integer frequenceJours;
    private List<RetourTerrain> historiqueVisites;
}
