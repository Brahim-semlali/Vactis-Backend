package com.vactis.service.Activite;

import com.vactis.model.medecin.Medecin;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiviteIrregulierePolicyTest {

    private static final LocalDate EVALUATION = LocalDate.of(2026, 10, 31);

    @Test
    void acceptsAllEightDocumentedConditions() {
        Medecin medecin = medecin("RETENTION", LocalDate.of(2025, 1, 1));
        List<LocalDate> dates = List.of(
                LocalDate.of(2025, 1, 15),
                LocalDate.of(2025, 7, 20),
                LocalDate.of(2026, 10, 10));

        assertTrue(ActiviteIrregulierePolicy.estEligible(medecin, "RETENTION", dates, EVALUATION, false, false));
    }

    @Test
    void rejectsPresenceAtExactlyEightyPercent() {
        Medecin medecin = medecin("RETENTION", LocalDate.of(2025, 1, 1));
        List<LocalDate> dates = new ArrayList<>();
        for (int month = 1; month <= 8; month++) {
            dates.add(YearMonth.of(2026, month).atDay(10));
        }

        assertFalse(ActiviteIrregulierePolicy.estEligible(medecin, "RETENTION", dates, EVALUATION, false, false));
    }

    @Test
    void rejectsActivityExactlySixtyDaysAgo() {
        Medecin medecin = medecin("SURVEILLANCE", LocalDate.of(2025, 1, 1));
        List<LocalDate> dates = List.of(
                LocalDate.of(2025, 1, 15),
                LocalDate.of(2026, 7, 10),
                EVALUATION.minusDays(60));

        assertFalse(ActiviteIrregulierePolicy.estEligible(medecin, "SURVEILLANCE", dates, EVALUATION, false, false));
    }

    @Test
    void rejectsNonEligibleTheoreticalStatusesAndExcludedDoctors() {
        Medecin active = medecin("ACTIF_STABLE", LocalDate.of(2025, 1, 1));
        Medecin onboarding = medecin("ONBOARDING", LocalDate.of(2025, 1, 1));
        List<LocalDate> dates = eligibleDates();

        assertFalse(ActiviteIrregulierePolicy.estEligible(active, "ACTIF_STABLE", dates, EVALUATION, false, false));
        assertFalse(ActiviteIrregulierePolicy.estEligible(onboarding, "RETENTION", dates, EVALUATION, false, false));
    }

    @Test
    void rejectsManualReactivationJustifiedAbsenceAndConfirmedRecovery() {
        Medecin medecin = medecin("RETENTION", LocalDate.of(2025, 1, 1));
        List<LocalDate> dates = eligibleDates();

        medecin.setIsAReactiverManuel(true);
        assertFalse(ActiviteIrregulierePolicy.estEligible(medecin, "RETENTION", dates, EVALUATION, false, false));
        medecin.setIsAReactiverManuel(false);
        assertFalse(ActiviteIrregulierePolicy.estEligible(medecin, "RETENTION", dates, EVALUATION, true, false));
        assertFalse(ActiviteIrregulierePolicy.estEligible(medecin, "RETENTION", dates, EVALUATION, false, true));
    }

    @Test
    void rejectsInsufficientRelationshipOrFewerThanTwoActiveMonths() {
        Medecin recent = medecin("RETENTION", LocalDate.of(2026, 6, 1));
        Medecin established = medecin("RETENTION", LocalDate.of(2025, 1, 1));
        List<LocalDate> singleActiveMonth = List.of(LocalDate.of(2026, 10, 10));

        assertFalse(ActiviteIrregulierePolicy.estEligible(recent, "RETENTION", eligibleDates(), EVALUATION, false, false));
        assertFalse(ActiviteIrregulierePolicy.estEligible(established, "RETENTION", singleActiveMonth, EVALUATION, false, false));
    }

    @Test
    void accountCreationDateDoesNotSubstituteForKnownRelationshipDate() {
        Medecin medecin = medecin("RETENTION", null);
        medecin.setCreatedAt(EVALUATION.minusYears(1).atStartOfDay());

        assertFalse(ActiviteIrregulierePolicy.estEligible(
                medecin, "RETENTION", eligibleDates(), EVALUATION, false, false));
    }

            @Test
            void calculatesPresenceRateFromActiveMonthsAndReturnsUnknownWhenInputsAreMissing() {
            assertEquals(Double.valueOf(30.0), ActiviteIrregulierePolicy.tauxPresencePct(
                3L, LocalDate.of(2025, 1, 15), LocalDate.of(2025, 10, 31)));
            assertNull(ActiviteIrregulierePolicy.tauxPresencePct(
                null, LocalDate.of(2025, 1, 15), LocalDate.of(2025, 10, 31)));
            }

    private static Medecin medecin(String statut, LocalDate dateRelation) {
        Medecin medecin = new Medecin();
        medecin.setStatut(statut);
        medecin.setDatePremiereCollaboration(dateRelation);
        medecin.setIsAReactiverManuel(false);
        return medecin;
    }

    private static List<LocalDate> eligibleDates() {
        return List.of(
                LocalDate.of(2025, 1, 15),
                LocalDate.of(2025, 7, 20),
                LocalDate.of(2026, 10, 10));
    }
}