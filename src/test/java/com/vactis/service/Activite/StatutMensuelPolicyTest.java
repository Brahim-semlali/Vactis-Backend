package com.vactis.service.Activite;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatutMensuelPolicyTest {

    @Test
    void computesTheDocumentedMixedVariationAndStatusBoundaries() {
        assertEquals(20.0, StatutMensuelPolicy.variationMixte(20.0, 20.0));
        assertEquals("PROGRESSION", StatutMensuelPolicy.statutDepuisVariation(20.0001));
        assertEquals("ACTIF_STABLE", StatutMensuelPolicy.statutDepuisVariation(20.0));
        assertEquals("ACTIF_STABLE", StatutMensuelPolicy.statutDepuisVariation(-10.0));
        assertEquals("SURVEILLANCE", StatutMensuelPolicy.statutDepuisVariation(-10.0001));
        assertEquals("SURVEILLANCE", StatutMensuelPolicy.statutDepuisVariation(-40.0));
        assertEquals("RETENTION", StatutMensuelPolicy.statutDepuisVariation(-40.0001));
    }

    @Test
    void onboardingMustBeNoMoreThanOneMonthOldAtTheEvaluationDate() {
        LocalDate evaluation = LocalDate.of(2026, 10, 30);
        assertTrue(StatutMensuelPolicy.onboardingRecent(LocalDate.of(2026, 9, 30), evaluation));
        assertTrue(StatutMensuelPolicy.onboardingRecent(LocalDate.of(2026, 10, 30), evaluation));
        assertFalse(StatutMensuelPolicy.onboardingRecent(LocalDate.of(2026, 9, 29), evaluation));
        assertTrue(StatutMensuelPolicy.onboardingRecentPourMois(LocalDate.of(2026, 10, 1), YearMonth.of(2026, 10)));
    }

    @Test
    void inactivationBeginsAtSixtyDaysWithoutActivity() {
        LocalDate evaluation = LocalDate.of(2026, 10, 31);
        assertTrue(StatutMensuelPolicy.activiteRecente(evaluation.minusDays(59), evaluation));
        assertFalse(StatutMensuelPolicy.activiteRecente(evaluation.minusDays(60), evaluation));
        assertFalse(StatutMensuelPolicy.activiteRecente(evaluation.plusDays(1), evaluation));
    }
}