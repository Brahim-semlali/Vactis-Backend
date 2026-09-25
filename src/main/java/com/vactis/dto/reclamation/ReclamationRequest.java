package com.vactis.dto.reclamation;

import com.vactis.model.reclamation.CategorieReclamation;
import com.vactis.model.reclamation.PrioriteReclamation;
import com.vactis.model.reclamation.StatutReclamation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReclamationRequest {

    @NotNull(message = "L'ID du médecin est obligatoire.")
    private Long medecinId;

    @NotBlank(message = "La description de la réclamation est obligatoire.")
    private String description;

    private CategorieReclamation categorie = CategorieReclamation.AUTRE;

    private PrioriteReclamation priorite = PrioriteReclamation.MOYENNE;

    private StatutReclamation statut = StatutReclamation.OUVERTE;

    private String responsableTraitement;

    private String solutionApportee;

    private Long retourTerrainId;
}
