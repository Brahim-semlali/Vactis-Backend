package com.vactis.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BridgeToGoalPolicyTest {

    @Test
    void computesRunRateFromUnroundedYtdAverage() {
        assertEquals(24_002L, BridgeToGoalPolicy.runRateAnnuel(10_001L, 5));
        assertEquals(0L, BridgeToGoalPolicy.runRateAnnuel(10_001L, 0));
    }

    @Test
    void computesNonNegativeGapAndMonthlyEffort() {
        assertEquals(6_000L, BridgeToGoalPolicy.gapProjete(30_000L, 24_000L));
        assertEquals(0L, BridgeToGoalPolicy.gapProjete(20_000L, 24_000L));
        assertEquals(2_000L, BridgeToGoalPolicy.effortMensuel(6_000L, 3));
        assertEquals(6_000L, BridgeToGoalPolicy.effortMensuel(6_000L, 0));
    }

    @Test
    void appliesCoverageDecisionThresholdsAtExactBoundaries() {
        assertEquals("PORTEFEUILLE_SUFFISANT", BridgeToGoalPolicy.decisionCouverture(100.0));
        assertEquals("PROSPECTION_CIBLEE", BridgeToGoalPolicy.decisionCouverture(50.0));
        assertEquals("PROSPECTION_INDISPENSABLE", BridgeToGoalPolicy.decisionCouverture(25.0));
        assertEquals("ACQUISITION_NECESSAIRE", BridgeToGoalPolicy.decisionCouverture(24.9));
        assertEquals(100.0, BridgeToGoalPolicy.couverturePourcent(500L, 0L));
        assertEquals(100.0, BridgeToGoalPolicy.couverturePourcent(1_500L, 1_000L));
    }

    @Test
    void calculatesTheDocumentedActivationScenarios() {
        assertEquals(200L, BridgeToGoalPolicy.potentielActivable(1_000L, 0.20));
        assertEquals(350L, BridgeToGoalPolicy.potentielActivable(1_000L, 0.35));
        assertEquals(500L, BridgeToGoalPolicy.potentielActivable(1_000L, 0.50));
    }

    @Test
    void estimatesNewDoctorsWithTheDocumentedFiftyPercentRampUp() {
        assertEquals(5, BridgeToGoalPolicy.nouveauxMedecinsNecessaires(1_001L, 500L, 0.50));
        assertEquals(0, BridgeToGoalPolicy.nouveauxMedecinsNecessaires(1_001L, 0L, 0.50));
    }
}