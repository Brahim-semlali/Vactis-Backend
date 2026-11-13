package com.vactis.service;

import java.util.List;

public final class ActionHorizonPolicy {

    private ActionHorizonPolicy() {}

    public static int recommanderHorizon(
            String niveauIntensite,
            boolean silenceCritiqueStrategique
    ) {
        if (silenceCritiqueStrategique) return 3;
        if (niveauIntensite == null) return 30;
        return switch (niveauIntensite.toUpperCase(java.util.Locale.ROOT)) {
            case "CRITIQUE" -> 7;
            case "ELEVEE", "ÉLEVÉE" -> 15;
            default -> 30;
        };
    }

    public static double percentile(List<Double> scores, double quantile) {
        if (scores == null || scores.isEmpty()) return 0.0;
        if (quantile < 0.0 || quantile > 1.0) {
            throw new IllegalArgumentException("Le quantile doit être compris entre 0 et 1.");
        }
        List<Double> ordered = scores.stream().filter(score -> score != null).sorted().toList();
        if (ordered.isEmpty()) return 0.0;
        double position = quantile * (ordered.size() - 1);
        int lowerIndex = (int) Math.floor(position);
        int upperIndex = (int) Math.ceil(position);
        if (lowerIndex == upperIndex) return ordered.get(lowerIndex);
        double fraction = position - lowerIndex;
        return ordered.get(lowerIndex) + fraction * (ordered.get(upperIndex) - ordered.get(lowerIndex));
    }
}