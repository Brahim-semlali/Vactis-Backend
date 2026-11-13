package com.vactis.service.Activite;

public final class ScoreMetierPolicy {

    private ScoreMetierPolicy() {}

    public static double potentiel(Double note) {
        double noteUtilisee = note == null ? 3.0 : note;
        return clamp(noteUtilisee / 5.0 * 100.0);
    }

    public static double poidsEconomique(double caNormalise, double casNormalises) {
        return 0.50 * caNormalise + 0.50 * casNormalises;
    }

    public static double scoreValeur(double potentiel, double performanceCa, double poidsEconomique) {
        return 0.40 * potentiel + 0.40 * performanceCa + 0.20 * poidsEconomique;
    }

    public static double scoreRisque(double tendanceRisque, double silence, double scoreValeur) {
        double poidsValeur = (0.70 * (scoreValeur / 100.0) + 0.30);
        return clamp((0.60 * tendanceRisque + 0.40 * silence) * poidsValeur);
    }

    public static double intensite(double scoreValeur, double scoreRisque) {
        return clamp(0.60 * scoreValeur + 0.40 * scoreRisque);
    }

    public static double intensiteArrondie(double scoreValeur, double scoreRisque) {
        return Math.round(intensite(scoreValeur, scoreRisque) * 100.0) / 100.0;
    }

    public static double scoreUrgence(double risque, double phase, double intensite, double delai) {
        return clamp(0.40 * risque + 0.30 * phase + 0.20 * intensite + 0.10 * delai);
    }

    public static double facteurMaterialite(double perteCa, double seuilImpact) {
        if (seuilImpact <= 0.0) return 0.35;
        double impact = Math.min(1.0, Math.max(0.0, perteCa / seuilImpact));
        return 0.35 + 0.65 * impact;
    }

    public static double urgenceFinale(double urgenceBrute, double facteurMaterialite) {
        return Math.round(clamp(urgenceBrute * facteurMaterialite) * 100.0) / 100.0;
    }

    public static String segment(double scoreValeur) {
        if (scoreValeur >= 75.0) return "A";
        if (scoreValeur >= 60.0) return "B";
        if (scoreValeur >= 45.0) return "C";
        return "D";
    }

    public static String niveauIntensite(double score, double mediane, double percentile80) {
        if (score >= percentile80) return "CRITIQUE";
        if (score >= mediane) return "ELEVEE";
        return "NORMALE";
    }

    public static double pointsIntensite(double score, double mediane, double percentile80) {
        return switch (niveauIntensite(score, mediane, percentile80)) {
            case "CRITIQUE" -> 100.0;
            case "ELEVEE" -> 60.0;
            default -> 30.0;
        };
    }

    private static double clamp(double value) {
        return Math.min(100.0, Math.max(0.0, value));
    }
}