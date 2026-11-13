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
    private Double potentielSur100;
    private Double sousExploitation;
    private Double scoreOpportunite;
    private Double scoreValeur;
    private Double scoreRisque;
    private Double scoreSilence;
    private Double scoreReactivation;
    private String prioriteReactivation;
    private Double tauxPresencePct;
    private Integer joursSansActivite;
    private Double intervalleEffectif;
    private Double referenceCa;
    private Double realisationObjectifPct;
    private String fiabilite;
    private Long caRecuperationEstime;
    private Long ancienneteMois;
    private String decisionDirection;
    private String justification;
    private String pitchCommercial;
}
