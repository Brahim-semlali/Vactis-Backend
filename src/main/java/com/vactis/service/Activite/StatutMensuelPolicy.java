package com.vactis.service.Activite;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public final class StatutMensuelPolicy {

    private StatutMensuelPolicy() {}

    public static double variationMixte(double variationCa, double variationCas) {
        return 0.60 * variationCa + 0.40 * variationCas;
    }

    public static String statutDepuisVariation(double variationMixte) {
        if (variationMixte > 20.0) return "PROGRESSION";
        if (variationMixte >= -10.0) return "ACTIF_STABLE";
        if (variationMixte >= -40.0) return "SURVEILLANCE";
        return "RETENTION";
    }

    public static boolean onboardingRecent(LocalDate premiereActivite, LocalDate dateEvaluation) {
        return premiereActivite != null && dateEvaluation != null
                && !premiereActivite.isAfter(dateEvaluation)
                && !premiereActivite.plusMonths(1).isBefore(dateEvaluation);
    }

    public static boolean activiteRecente(LocalDate derniereActivite, LocalDate dateEvaluation) {
        return derniereActivite != null && dateEvaluation != null
                && !derniereActivite.isAfter(dateEvaluation)
                && ChronoUnit.DAYS.between(derniereActivite, dateEvaluation) < 60;
    }

    public static boolean onboardingRecentPourMois(LocalDate premiereActivite, YearMonth moisEvaluation) {
        return moisEvaluation != null && onboardingRecent(premiereActivite, moisEvaluation.atEndOfMonth());
    }
}