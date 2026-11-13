package com.vactis.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactivationPolicyTest {

    @Test
    void proximityFallsToZeroAfterOneYearWithoutActivity() {
        LocalDate reference = LocalDate.of(2026, 10, 25);

        assertEquals(100.0, ReactivationPolicy.proximityScore(reference, reference));
        assertEquals(0.0, ReactivationPolicy.proximityScore(reference.minusDays(365), reference));
        assertEquals(0.0, ReactivationPolicy.proximityScore(reference.minusDays(500), reference));
    }

    @Test
    void regularityIsTheShareOfActiveMonthsSinceFirstActivity() {
        assertEquals(50.0, ReactivationPolicy.regularityScore(
                3, LocalDate.of(2026, 1, 3), LocalDate.of(2026, 6, 30)));
    }

    @Test
    void ranksScoresAtTheFortiethAndSeventyFifthPercentiles() {
        List<Double> scores = List.of(10.0, 20.0, 30.0, 40.0);

        assertEquals(ReactivationPolicy.Priority.LOW, ReactivationPolicy.priority(10.0, scores));
        assertEquals(ReactivationPolicy.Priority.MEDIUM, ReactivationPolicy.priority(22.0, scores));
        assertEquals(ReactivationPolicy.Priority.HIGH, ReactivationPolicy.priority(32.5, scores));
    }

    @Test
    void doesNotAssignHighPriorityWithoutAComparablePeer() {
        assertEquals(ReactivationPolicy.Priority.LOW, ReactivationPolicy.priority(100.0, List.of(100.0)));
        assertEquals(ReactivationPolicy.Priority.LOW, ReactivationPolicy.priority(100.0, List.of()));
        assertEquals(ReactivationPolicy.Priority.LOW, ReactivationPolicy.priority(50.0, List.of(50.0, 50.0)));
    }

    @Test
    void calculatesTheDocumentedWeightedScore() {
        assertEquals(50.0, ReactivationPolicy.score(50.0, 50.0, 50.0));
        assertEquals(40.0, ReactivationPolicy.score(100.0, 0.0, 0.0));
    }
}