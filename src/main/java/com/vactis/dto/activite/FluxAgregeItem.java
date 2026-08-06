package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Paire de transition (statut M-1 → statut M) avec effectif et liste des médecins concernés
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FluxAgregeItem {

    private String statutPrecedent;            // statut VACTIS en M-1
    private String statutCourant;              // statut VACTIS en M
    private String couleurPrecedent;           // couleur CSS du statut M-1
    private String couleurCourant;             // couleur CSS du statut M
    private long nombreMedecins;               // nombre de médecins ayant effectué cette transition
    private List<MedecinStatutItem> medecins;  // liste des médecins concernés
}
