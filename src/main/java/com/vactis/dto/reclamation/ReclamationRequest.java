package com.vactis.dto.reclamation;

import com.vactis.model.reclamation.CategorieReclamation;
import com.vactis.model.reclamation.PrioriteReclamation;
import com.vactis.model.reclamation.StatutReclamation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ReclamationRequest {

    @NotNull(message = "L'ID du médecin est obligatoire.")
    private Long medecinId;

    @NotNull(message = "La date de visite est obligatoire.")
    @PastOrPresent(message = "La date de visite ne peut pas être dans le futur.")
    private LocalDate dateVisite;

    @NotBlank(message = "La description de la réclamation est obligatoire.")
    private String description;

    private CategorieReclamation categorie = CategorieReclamation.AUTRE;

    private PrioriteReclamation priorite = PrioriteReclamation.MOYENNE;

    private StatutReclamation statut = StatutReclamation.A_TRAITER;

    private String responsableTraitement;

    private String solutionApportee;

    private String prochaineInstruction;

    private Long retourTerrainId;

    // Échéance de traitement (facultative)
    private LocalDate dateEcheance;
}
