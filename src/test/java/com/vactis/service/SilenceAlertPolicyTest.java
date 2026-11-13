package com.vactis.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilenceAlertPolicyTest {

    @Test
    void recentRhythmReliabilityUsesAnyOneOfTheThreeAlternatives() {
        assertTrue(SilenceAlertPolicy.rythmeRecentFiable(5, 1, 1));
        assertTrue(SilenceAlertPolicy.rythmeRecentFiable(4, 2, 1));
        assertTrue(SilenceAlertPolicy.rythmeRecentFiable(4, 1, 3));
        assertFalse(SilenceAlertPolicy.rythmeRecentFiable(4, 1, 2));
    }

    @Test
    void silenceRatioThresholdsAreExact() {
        assertEquals(SilenceAlertPolicy.NiveauRupture.AUCUNE, SilenceAlertPolicy.niveauRupture(1.499));
        assertEquals(SilenceAlertPolicy.NiveauRupture.SURVEILLANCE, SilenceAlertPolicy.niveauRupture(1.5));
        assertEquals(SilenceAlertPolicy.NiveauRupture.SURVEILLANCE, SilenceAlertPolicy.niveauRupture(1.999));
        assertEquals(SilenceAlertPolicy.NiveauRupture.CRITIQUE, SilenceAlertPolicy.niveauRupture(2.0));
    }

    @Test
    void urgencyFormulaAndAllStrategicConditionsAreRequired() {
        assertEquals(85.0, SilenceAlertPolicy.scoreUrgence(2.0, 50.0, 100.0));
        assertTrue(SilenceAlertPolicy.alerteStrategiqueEligible(
                "RETENTION", "A", 4.0, true, true, 2.0, false));
        assertTrue(SilenceAlertPolicy.alerteStrategiqueEligible(
                "ACTIVITE_IRREGULIERE", "B", 4.0, true, true, 2.0, true));
        assertFalse(SilenceAlertPolicy.alerteStrategiqueEligible(
                "RETENTION", "C", 4.0, true, true, 2.0, false));
        assertFalse(SilenceAlertPolicy.alerteStrategiqueEligible(
                "RETENTION", "A", 3.99, true, true, 2.0, false));
        assertFalse(SilenceAlertPolicy.alerteStrategiqueEligible(
                "RETENTION", "A", 4.0, false, true, 2.0, false));
        assertFalse(SilenceAlertPolicy.alerteStrategiqueEligible(
                "RETENTION", "A", 4.0, true, false, 2.0, false));
        assertFalse(SilenceAlertPolicy.alerteStrategiqueEligible(
                "RETENTION", "A", 4.0, true, true, 1.999, false));
    }
}