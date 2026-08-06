package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Représente un médecin inclus dans un statut VACTIS ou une transition de flux
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedecinStatutItem {

    private Long id;
    private String codeMedecin;
    private String nom;          // nom complet (nom + prénom)
    private String specialite;
    private long caM;            // CA du mois M
    private long casM;           // nombre de cas du mois M
}
