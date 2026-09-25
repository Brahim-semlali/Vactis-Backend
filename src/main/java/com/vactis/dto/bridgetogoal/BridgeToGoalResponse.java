package com.vactis.dto.bridgetogoal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BridgeToGoalResponse {
    private String periode;
    private Long caActuel;
    private Long gainOnboarding;
    private Long gainRetention;
    private Long gainDeveloppement;
    private Long churnEstime;
    private Long caProjete;
    private Long targetBudget;
    private Long gap;
    private Integer actionsRequises;
    private String statutTrajectoire; // ATTEINT, TENDU, CRITIQUE
    private Double tauxAtteinte;
    private Integer nbMedecinsOnboarding;
    private Integer nbMedecinsEnRetention;
    private Integer nbMedecinsDeveloppement;
    private Integer nbReclamationsBloquantes;
    private Long caMoisTotal;
    private Long casMoisTotal;
    private Long caPortefeuille;
    private Integer nbMedecinsActifs;
    private Long caYtdReel;
    private Integer moisEcoules;
    private Long caNMoins1Comparable;
    private Long caMoyenMensuel;
    private Long objectifYtdProratise;
    private Long varianceYtd;
    private Double varianceYtdPct;
    private Long runRateAnnuel;
    private Long gapProjete;
    private Integer moisRestants;
    private Long effortAdditionnelMensuel;
    private String statutYtd;
    private Integer nbMedecinsSegmentA;
    private Integer nbMedecinsSegmentB;
    private Integer nbMedecinsSegmentC;
    private Integer nbMedecinsSegmentD;
    private Long nonAffectesCount;
    private Long medecinsIdentifies;
    private Long caMoyenSegmentA;
    private Long caMoyenSegmentB;
    private Long caMoyenSegmentC;
    private Long caMoyenSegmentD;
    private Integer nouveauxMedecinsSegmentA;
    private Integer nouveauxMedecinsSegmentB;
    private Integer nouveauxMedecinsSegmentC;
    private Integer nouveauxMedecinsSegmentD;
}
