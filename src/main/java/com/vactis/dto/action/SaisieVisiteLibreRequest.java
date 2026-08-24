package com.vactis.dto.action;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class SaisieVisiteLibreRequest {

    /** Existing doctor ID (optional if creating new doctor). */
    private Long medecinId;

    /** Fields for creating a new doctor if medecinId is null. */
    private String nomMedecin;
    private String prenomMedecin;
    private String specialite;
    private String organisme;

    /** Visit details. */
    private LocalDate dateVisite;
    private Boolean actionRealisee = true;
    private String motifNonRealisation;
    private String qualification;
    private String commentaire;
    private Double noteTerrain;
    private String prochaineAction;
    private LocalDate dateProchaineAction;
}
