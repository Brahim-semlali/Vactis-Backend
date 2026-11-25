package com.vactis.model.Controle;

public enum TypeControle {
    STATUT , SEGEMENTS ,
    /** Ratio silence / intervalle moyen (en %) -> niveau de rupture de rythme */
    RUPTURE ,
    /** Score d'urgence finale (0-100) -> niveau d'urgence médecin */
    URGENCE ,
    /** Nombre de mois actifs -> fiabilité de l'historique */
    FIABILITE ,
    /** Niveau d'intensité -> délai d'action recommandé (jours) */
    DELAI_ACTION
}
