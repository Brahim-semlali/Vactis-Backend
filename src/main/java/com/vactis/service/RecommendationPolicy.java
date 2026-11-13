package com.vactis.service;

import java.util.Locale;
import java.util.Set;

public final class RecommendationPolicy {

    private static final Set<String> DEVELOPMENT_STATUSES_EXCLUDED = Set.of(
            "INACTIF", "EXCLU", "ACTIVITE_IRREGULIERE");

    private RecommendationPolicy() {}

    public static double sousExploitation(Double caReference, Double caMoyenMensuel) {
        if (caReference == null || caReference <= 0.0) return 0.0;
        double caActuel = caMoyenMensuel == null ? 0.0 : caMoyenMensuel;
        return caReference / Math.max(caActuel, 1.0);
    }

    public static boolean eligibleDeveloppement(
            String statut,
            boolean profilIrregulier,
            Double potentielSur100,
            Double noteTerrain,
            double scoreSousExploitation
    ) {
        String statutNormalise = statut == null ? "" : statut.trim().toUpperCase(Locale.ROOT);
        boolean potentielConfirme = (potentielSur100 != null && potentielSur100 >= 80.0)
                || (noteTerrain != null && noteTerrain >= 4.0);
        return !profilIrregulier
                && !DEVELOPMENT_STATUSES_EXCLUDED.contains(statutNormalise)
                && scoreSousExploitation > 2.0
                && potentielConfirme;
    }
}