package com.vactis.dto.action;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO léger pour l'affichage des visites commerciales libres (hors VACTIS) dans le tableau frontend. */
@Data
@NoArgsConstructor
public class VisiteLibreResponse {

    private Long id;
    private LocalDate dateVisite;
    private String visiteur;
    private String qualification;
    private String commentaire;
    /** SERVICE pour un passage de service, sinon type commercial ou null. */
    private String typeVisite;
    private String commercialAppui;
    private LocalDateTime createdAt;

    // Champs médecin dénormalisés — évite la sérialisation lazy de l'entité Medecin
    private Long medecinId;
    private String medecinNom;
    private String medecinPrenom;
    private String medecinSpecialite;
    private String medecinOrganisme;
    private String medecinVille;
}
