package com.vactis.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionHorizonPolicyTest {

    private static final List<Double> SCORES = List.of(10.0, 20.0, 30.0, 40.0, 50.0);

    @Test
    void mapsIntensityCategoriesToDocumentedVisitHorizons() {
        assertEquals(30, ActionHorizonPolicy.recommanderHorizon("NORMALE", false));
        assertEquals(15, ActionHorizonPolicy.recommanderHorizon("ELEVEE", false));
        assertEquals(15, ActionHorizonPolicy.recommanderHorizon("ÉLEVÉE", false));
        assertEquals(7, ActionHorizonPolicy.recommanderHorizon("CRITIQUE", false));
        assertEquals(30, ActionHorizonPolicy.recommanderHorizon(null, false));
    }

    @Test
    void strategicCriticalSilenceKeepsThreeDayOverride() {
        assertEquals(3, ActionHorizonPolicy.recommanderHorizon("ELEVEE", true));
    }

    @Test
    void rejectsInvalidQuantiles() {
        assertThrows(IllegalArgumentException.class, () -> ActionHorizonPolicy.percentile(SCORES, 1.1));
    }

}