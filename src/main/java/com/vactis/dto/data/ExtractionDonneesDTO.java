package com.vactis.dto.data;

import com.vactis.model.data.StatutDossier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtractionDonneesDTO {
    private Long id;
    private Long medecinId;
    private String codeMedecin;
    private String nomMedecin;
    private String referenceDossier;
    private LocalDate dateReception;
    private LocalDate datePrelevement;
    private String typeAnalyse;
    private Integer nombreAnalyses;
    private StatutDossier statutDossier;
    private String lieuPrelevement;
    private String patientRef;
    private Integer prixTotal;
    private Integer montantRembourse;
    private Integer prixAPayer;
    private boolean urgent;
}
