package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Entrée du classement Top mouvements pour un médecin (delta CA ou cas entre M-1 et M)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopMouvementItem {

    private String nomMedecin; // nom complet en majuscules
    private String specialite;
    private long valeurM;      // valeur de la métrique pour le mois M
    private long valeurMm1;    // valeur de la métrique pour le mois M-1
    private long delta;        // valeurM - valeurMm1 (positif = progression, négatif = baisse)
    private String unite;      // "MAD" pour le CA, "cas" pour le volume
}
