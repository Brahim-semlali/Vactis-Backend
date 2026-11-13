package com.vactis.service.Activite;

import com.vactis.model.medecin.Medecin;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class ActiviteIrregulierePolicy {

    private static final Set<String> STATUTS_THEORIQUES_ELIGIBLES = Set.of("SURVEILLANCE", "RETENTION");
    private static final Set<String> STATUTS_EXCLUS = Set.of("ONBOARDING", "EXCLU", "A_REACTIVER");

    private ActiviteIrregulierePolicy() {}

    public static boolean estEligible(
            Medecin medecin,
            String statutTheorique,
            List<LocalDate> datesActivite,
            LocalDate dateEvaluation,
            boolean absenceJustifieeEnCours,
            boolean repriseConfirmeeApresRetention
    ) {
        if (medecin == null || dateEvaluation == null || datesActivite == null || datesActivite.isEmpty()) {
            return false;
        }

        String statutCourant = medecin.getStatut() == null
                ? ""
                : medecin.getStatut().trim().toUpperCase(Locale.ROOT);
        String statutReference = statutTheorique == null
                ? ""
                : statutTheorique.trim().toUpperCase(Locale.ROOT);

        if (!STATUTS_THEORIQUES_ELIGIBLES.contains(statutReference)
                || STATUTS_EXCLUS.contains(statutCourant)
                || Boolean.TRUE.equals(medecin.getIsAReactiverManuel())
                || absenceJustifieeEnCours
                || repriseConfirmeeApresRetention) {
            return false;
        }

        LocalDate dateRelation = medecin.getDatePremiereCollaboration();
        if (dateRelation == null || dateRelation.plusMonths(6).isAfter(dateEvaluation)) {
            return false;
        }

        List<LocalDate> datesValides = datesActivite.stream()
                .filter(date -> date != null && !date.isAfter(dateEvaluation))
                .toList();
        if (datesValides.isEmpty()) {
            return false;
        }

        LocalDate premiereActivite = datesValides.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate derniereActivite = datesValides.stream().max(LocalDate::compareTo).orElseThrow();
        long joursDepuisActivite = ChronoUnit.DAYS.between(derniereActivite, dateEvaluation);
        if (joursDepuisActivite >= 60) {
            return false;
        }

        Set<YearMonth> moisActifs = datesValides.stream()
                .filter(date -> !date.isBefore(premiereActivite))
                .map(YearMonth::from)
                .collect(Collectors.toSet());
        if (moisActifs.size() < 2) {
            return false;
        }

        Double tauxPresence = tauxPresencePct((long) moisActifs.size(), premiereActivite, dateEvaluation);
        return tauxPresence != null && tauxPresence < 80.0;
        }

        public static Double tauxPresencePct(Long moisActifs, LocalDate premiereActivite, LocalDate dateEvaluation) {
        if (moisActifs == null || moisActifs <= 0 || premiereActivite == null || dateEvaluation == null
            || premiereActivite.isAfter(dateEvaluation)) {
            return null;
        }
        long moisDepuisPremiereActivite = ChronoUnit.MONTHS.between(
            YearMonth.from(premiereActivite), YearMonth.from(dateEvaluation)) + 1;
        if (moisDepuisPremiereActivite <= 0) return null;
        return moisActifs * 100.0 / moisDepuisPremiereActivite;
    }
}