package com.vactis.service.Activite;

import com.vactis.service.ActionHorizonPolicy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class IndicateursMensuelsPolicy {

    public record SilenceMetrics(LocalDate dateDerniereActivite, double intervalleEffectif, String sourceRythme,
                                 int joursSansActivite, double scoreSilence,
                                 String classeSilence, double ratioSilence) {}

    private IndicateursMensuelsPolicy() {}

    public static double moyenneReference(List<Double> moisDisponibles) {
        if (moisDisponibles == null || moisDisponibles.isEmpty()) return 0.0;
        return moisDisponibles.stream().mapToDouble(value -> value == null ? 0.0 : value).average().orElse(0.0);
    }

    public static double variation(double courant, double reference, double plancher) {
        if (reference <= 0.0) return 0.0;
        return (courant - reference) / Math.max(reference, plancher) * 100.0;
    }

    public static double seuilImpact(List<Double> caMoyensEligibles, List<Double> caMoyensPositifs) {
        List<Double> eligibles = caMoyensEligibles == null ? List.of() : caMoyensEligibles.stream()
                .filter(value -> value != null && value > 0.0)
                .sorted((left, right) -> Double.compare(right, left))
                .toList();
        if (!eligibles.isEmpty()) {
            int topCount = Math.max(1, (int) Math.ceil(eligibles.size() * 0.20));
            return ActionHorizonPolicy.percentile(eligibles.subList(0, Math.min(topCount, eligibles.size())), 0.50);
        }
        List<Double> positifs = caMoyensPositifs == null ? List.of() : caMoyensPositifs.stream()
                .filter(value -> value != null && value > 0.0)
                .toList();
        return positifs.isEmpty() ? 1.0 : ActionHorizonPolicy.percentile(positifs, 0.90);
    }

    public static SilenceMetrics silence(List<LocalDate> dates, LocalDate reference) {
        List<LocalDate> datesAvantReference = dates == null ? List.of() : dates.stream()
                .filter(date -> date != null && !date.isAfter(reference))
                .sorted()
                .toList();
        LocalDate derniereActivite = datesAvantReference.isEmpty()
                ? null : datesAvantReference.get(datesAvantReference.size() - 1);
        int joursSansActivite = derniereActivite == null
                ? 0 : Math.max(0, Math.toIntExact(ChronoUnit.DAYS.between(derniereActivite, reference)));

        LocalDate debutFenetre = reference.minusDays(89);
        List<LocalDate> datesRecentes = datesAvantReference.stream()
                .filter(date -> !date.isBefore(debutFenetre))
                .toList();
        long moisActifsRecents = datesRecentes.stream().map(YearMonth::from).distinct().count();
        long datesDistinctesRecentes = datesRecentes.stream().distinct().count();

        double intervalleEffectif;
        String sourceRythme;
        boolean rythmeRecentFiable = datesRecentes.size() >= 5
                || moisActifsRecents >= 2
                || datesDistinctesRecentes >= 3;
        if (rythmeRecentFiable) {
            LocalDate premierEnvoiRecent = datesRecentes.get(0);
            long joursEcoules = ChronoUnit.DAYS.between(premierEnvoiRecent, reference) + 1;
            intervalleEffectif = Math.max(1.0, (double) joursEcoules / datesRecentes.size());
            sourceRythme = "RECENT";
        } else if (datesAvantReference.size() >= 2) {
            intervalleEffectif = intervalleHistorique(datesAvantReference);
            sourceRythme = "HISTORIQUE";
        } else {
            intervalleEffectif = 30.0;
            sourceRythme = "DEFAUT_30_JOURS";
        }

        double ratioSilence = joursSansActivite / intervalleEffectif;
        double scoreSilence = Math.min(100.0, 20.0 * ratioSilence);
        String classeSilence = classeSilence(ratioSilence);
        return new SilenceMetrics(derniereActivite, intervalleEffectif, sourceRythme, joursSansActivite,
                scoreSilence, classeSilence, ratioSilence);
    }

        public static String classeSilence(double ratioSilence) {
        return ratioSilence >= 2.0 ? "CRITIQUE"
            : ratioSilence >= 1.5 ? "A_SURVEILLER" : "AUCUNE_RUPTURE";
        }

    private static double intervalleHistorique(List<LocalDate> dates) {
        int intervals = Math.min(dates.size() - 1, 3);
        long totalDays = 0;
        for (int index = dates.size() - 1; index > dates.size() - 1 - intervals; index--) {
            totalDays += ChronoUnit.DAYS.between(dates.get(index - 1), dates.get(index));
        }
        return Math.max(1.0, (double) totalDays / intervals);
    }
}