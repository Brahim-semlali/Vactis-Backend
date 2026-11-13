package com.vactis.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class ReactivationPolicy {

    public enum Priority { HIGH, MEDIUM, LOW }

    private ReactivationPolicy() {}

    public static double proximityScore(LocalDate lastActivity, LocalDate reference) {
        if (lastActivity == null || reference == null || lastActivity.isAfter(reference)) return 0.0;
        long daysInactive = ChronoUnit.DAYS.between(lastActivity, reference);
        return Math.max(0.0, 100.0 * (1.0 - daysInactive / 365.0));
    }

    public static double regularityScore(long activeMonths, LocalDate firstActivity, LocalDate reference) {
        if (activeMonths <= 0 || firstActivity == null || reference == null || firstActivity.isAfter(reference)) return 0.0;
        long elapsedMonths = ChronoUnit.MONTHS.between(YearMonth.from(firstActivity), YearMonth.from(reference)) + 1;
        if (elapsedMonths <= 0) return 0.0;
        return Math.min(100.0, activeMonths * 100.0 / elapsedMonths);
    }

    public static double score(double valueScore, double proximityScore, double regularityScore) {
        double boundedValue = Math.max(0.0, Math.min(100.0, valueScore));
        double boundedProximity = Math.max(0.0, Math.min(100.0, proximityScore));
        double boundedRegularity = Math.max(0.0, Math.min(100.0, regularityScore));
        return Math.round((0.40 * boundedValue + 0.30 * boundedProximity + 0.30 * boundedRegularity) * 100.0) / 100.0;
    }

    public static Priority priority(double score, List<Double> populationScores) {
        if (populationScores == null) {
            return Priority.LOW;
        }
        List<Double> comparableScores = populationScores.stream().filter(value -> value != null).distinct().toList();
        if (comparableScores.size() < 2) return Priority.LOW;
        double p75 = ActionHorizonPolicy.percentile(populationScores, 0.75);
        double p40 = ActionHorizonPolicy.percentile(populationScores, 0.40);
        if (score >= p75) return Priority.HIGH;
        if (score >= p40) return Priority.MEDIUM;
        return Priority.LOW;
    }
}
