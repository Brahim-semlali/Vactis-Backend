package com.vactis.dto.recommandation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommandationItemResponse {
    private Long medecinId;
    private String nomMedecin;
    private String specialite;
    private String organisme;
    private String segment;
    private String statut;
    private String typeRecommandation;
    private Long caMoyenMensuel;
    private Long totalCasHistorique;
    private Double noteTerrain;
    private String justification;
    private String pitchCommercial;
}
