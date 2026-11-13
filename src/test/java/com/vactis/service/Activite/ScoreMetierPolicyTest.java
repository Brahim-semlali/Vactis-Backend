package com.vactis.service.Activite;

import com.vactis.service.ActionHorizonPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreMetierPolicyTest {

    @Test
    void usesThreeOutOfFiveAsTheDefaultPotential() {
        assertEquals(60.0, ScoreMetierPolicy.potentiel(null));
        assertEquals(80.0, ScoreMetierPolicy.potentiel(4.0));
    }

    @Test
    void computesDocumentedEconomicValueRiskIntensityAndUrgencyWeights() {
        assertEquals(80.0, ScoreMetierPolicy.poidsEconomique(100.0, 60.0));
        assertEquals(80.0, ScoreMetierPolicy.scoreValeur(100.0, 75.0, 50.0));
        assertEquals(40.0, ScoreMetierPolicy.scoreRisque(50.0, 25.0, 100.0));
        assertEquals(64.0, ScoreMetierPolicy.intensite(80.0, 40.0));
        assertEquals(55.0, ScoreMetierPolicy.scoreUrgence(40.0, 60.0, 80.0, 50.0));
    }

    @Test
    void usesExactDocumentedSegmentRanges() {
        assertEquals("A", ScoreMetierPolicy.segment(75.0));
        assertEquals("B", ScoreMetierPolicy.segment(60.0));
        assertEquals("C", ScoreMetierPolicy.segment(45.0));
        assertEquals("D", ScoreMetierPolicy.segment(44.999));
    }

    @Test
    void classifiesIntensityAtP80ThenMedian() {
        assertEquals("CRITIQUE", ScoreMetierPolicy.niveauIntensite(80.0, 50.0, 80.0));
        assertEquals("ELEVEE", ScoreMetierPolicy.niveauIntensite(50.0, 50.0, 80.0));
        assertEquals("NORMALE", ScoreMetierPolicy.niveauIntensite(49.99, 50.0, 80.0));
        assertEquals(100.0, ScoreMetierPolicy.pointsIntensite(80.0, 50.0, 80.0));
        assertEquals(60.0, ScoreMetierPolicy.pointsIntensite(50.0, 50.0, 80.0));
        assertEquals(30.0, ScoreMetierPolicy.pointsIntensite(49.99, 50.0, 80.0));
    }

    @Test
    void reproducesMehdiUrgencyWithElevatedIntensityPoints() {
        double rawUrgency = ScoreMetierPolicy.scoreUrgence(7.2414, 100.0, 60.0, 0.0);

        assertEquals(17.81, ScoreMetierPolicy.urgenceFinale(rawUrgency, 0.39661), 0.001);
    }

    @Test
    void classifiesDisplayedIntensityAtDisplayedMedianAfterRounding() {
        List<Double> intensities = new ArrayList<>();
        for (int value = 10; value < 22; value++) intensities.add((double) value);
        intensities.add(ScoreMetierPolicy.intensiteArrondie(55.08, 7.24));
        for (int value = 40; value < 52; value++) intensities.add((double) value);
        double median = ActionHorizonPolicy.percentile(intensities, 0.50);

        assertEquals(35.94, median, 0.001);
        assertEquals(35.94, ScoreMetierPolicy.intensiteArrondie(55.08, 7.24), 0.001);
        assertEquals("ELEVEE", ScoreMetierPolicy.niveauIntensite(35.94, median, 43.07));
        assertEquals(60.0, ScoreMetierPolicy.pointsIntensite(35.94, median, 43.07));
    }
}