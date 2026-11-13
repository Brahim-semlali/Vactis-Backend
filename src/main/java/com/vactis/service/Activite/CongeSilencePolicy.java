package com.vactis.service.Activite;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class CongeSilencePolicy {

    private CongeSilencePolicy() {}

    /**
     * Calcule le nombre de jours sans activité en excluant les jours d'absence justifiée (congé)
     * survenus entre la date de dernière activité et la date d'évaluation (Section 5).
     */
    public static int calculerJoursSansActiviteAjustes(
            LocalDate dateDerniereActivite,
            LocalDate dateEvaluation,
            LocalDate dateDepartConge,
            LocalDate dateRetourPrevue,
            LocalDate dateRevue
    ) {
        if (dateDerniereActivite == null || dateEvaluation == null || dateDerniereActivite.isAfter(dateEvaluation)) {
            return 0;
        }
        int joursBruts = (int) ChronoUnit.DAYS.between(dateDerniereActivite, dateEvaluation);
        if (dateDepartConge == null) {
            return joursBruts;
        }

        LocalDate dateFinConge = dateRetourPrevue != null ? dateRetourPrevue : dateRevue;
        if (dateFinConge == null) {
            dateFinConge = dateEvaluation;
        }

        LocalDate debutCongeEffectif = dateDepartConge.isBefore(dateDerniereActivite) ? dateDerniereActivite : dateDepartConge;
        LocalDate finCongeEffectif = dateFinConge.isAfter(dateEvaluation) ? dateEvaluation : dateFinConge;

        if (debutCongeEffectif.isAfter(finCongeEffectif)) {
            return joursBruts;
        }

        int joursAbsenceExclus = (int) ChronoUnit.DAYS.between(debutCongeEffectif, finCongeEffectif);
        return Math.max(0, joursBruts - joursAbsenceExclus);
    }
}
