package com.vactis.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationPolicyTest {

    @Test
    void requiresStrictlyMoreThanTwoAndConfirmedPotential() {
        assertFalse(RecommendationPolicy.eligibleDeveloppement("ACTIF_STABLE", false, 80.0, null, 2.0));
        assertTrue(RecommendationPolicy.eligibleDeveloppement("ACTIF_STABLE", false, 80.0, null, 2.01));
        assertTrue(RecommendationPolicy.eligibleDeveloppement("PROGRESSION", false, null, 4.0, 2.1));
        assertFalse(RecommendationPolicy.eligibleDeveloppement("PROGRESSION", false, null, 3.99, 2.1));
    }

    @Test
    void excludesInactiveExcludedAndIrregularProfiles() {
        assertFalse(RecommendationPolicy.eligibleDeveloppement("INACTIF", false, 90.0, null, 3.0));
        assertFalse(RecommendationPolicy.eligibleDeveloppement("EXCLU", false, 90.0, null, 3.0));
        assertFalse(RecommendationPolicy.eligibleDeveloppement("ACTIVITE_IRREGULIERE", false, 90.0, null, 3.0));
        assertFalse(RecommendationPolicy.eligibleDeveloppement("ACTIF_STABLE", true, 90.0, null, 3.0));
    }

    @Test
    void computesReferenceToCurrentActivityRatio() {
        assertTrue(RecommendationPolicy.sousExploitation(100.0, 40.0) > 2.0);
        assertTrue(RecommendationPolicy.sousExploitation(100.0, 0.0) > 2.0);
    }
}