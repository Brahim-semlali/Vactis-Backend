package com.vactis.dto.action;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class SaisieRetourTerrainRequest {

    /** Indicates whether the action was completed ("Oui" -> true, "Non" -> false). */
    private Boolean actionRealisee;

    /** Real visit date. */
    private LocalDate dateVisite;

    /** Mandatory if actionRealisee = false. */
    private String motifNonRealisation;

    /** Qualification: FAVORABLE, NEUTRE, DEFAVORABLE, RECLAMATION. */
    private String qualification;

    /** Mandatory if qualification = RECLAMATION. */
    private String commentaire;

    /** Optional rating note for the field visit (1.0 to 5.0). */
    private Double noteTerrain;

    /** Next suggested action. */
    private String prochaineAction;

    /** Due date for next action. */
    private LocalDate dateProchaineAction;
}
