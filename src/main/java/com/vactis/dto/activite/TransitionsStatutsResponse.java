package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 5 compteurs agrégés de transitions de statuts VACTIS entre M-1 et M
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitionsStatutsResponse {

    private String moisPrecedent;
    private String moisCourant;
    private long totalEtudies;      // médecins avec statut M-1 ET M calculés
    private long favorables;        // rang amélioré entre M-1 et M
    private long stables;           // même statut entre M-1 et M
    private long defavorables;      // rang dégradé entre M-1 et M
    private long nouveauxMedecins;  // onboarding en M sans statut M-1
}
