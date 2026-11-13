package com.vactis.service.Activite;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndicateursMensuelsPolicyTest {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 30);

    @Test
    void averagesOnlyAvailableMonthsAndPreservesZeroMonths() {
        assertEquals(3556.6666667, IndicateursMensuelsPolicy.moyenneReference(List.of(5020.0, 3670.0, 1980.0)), 0.0001);
        assertEquals(2510.0, IndicateursMensuelsPolicy.moyenneReference(List.of(5020.0, 0.0)), 0.0001);
    }

    @Test
    void usesRecentRhythmWhenWindowHasThreeDistinctDates() {
        var metrics = IndicateursMensuelsPolicy.silence(List.of(
                LocalDate.of(2026, 7, 3),
                LocalDate.of(2026, 7, 4),
                LocalDate.of(2026, 7, 5)), REFERENCE);

        assertEquals("RECENT", metrics.sourceRythme());
    assertEquals(30.0, metrics.intervalleEffectif(), 0.0001);
        assertEquals(87, metrics.joursSansActivite());
    }

    @Test
    void fallsBackToHistoricalRhythmWhenRecentWindowIsNotReliable() {
        var metrics = IndicateursMensuelsPolicy.silence(List.of(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 11),
                LocalDate.of(2026, 4, 21),
                LocalDate.of(2026, 4, 30)), REFERENCE);

        assertEquals("HISTORIQUE", metrics.sourceRythme());
        assertEquals(29.0 / 3.0, metrics.intervalleEffectif(), 0.0001);
    }

    @Test
    void fallsBackToThirtyDaysWithoutEnoughHistory() {
        var metrics = IndicateursMensuelsPolicy.silence(List.of(LocalDate.of(2026, 9, 8)), REFERENCE);

        assertEquals("DEFAUT_30_JOURS", metrics.sourceRythme());
        assertEquals(30.0, metrics.intervalleEffectif());
        assertEquals(22, metrics.joursSansActivite());
        assertEquals(14.6666667, metrics.scoreSilence(), 0.0001);
    }

    @Test
    void appliesExactSilenceClassThresholds() {
        assertEquals("AUCUNE_RUPTURE", IndicateursMensuelsPolicy.classeSilence(1.49));
        assertEquals("A_SURVEILLER", IndicateursMensuelsPolicy.classeSilence(1.5));
        assertEquals("A_SURVEILLER", IndicateursMensuelsPolicy.classeSilence(1.99));
        assertEquals("CRITIQUE", IndicateursMensuelsPolicy.classeSilence(2.0));
    }

    @Test
    void returnsZeroVariationWhenReferenceIsZero() {
        assertEquals(0.0, IndicateursMensuelsPolicy.variation(760.0, 0.0, 300.0));
        assertEquals(-78.63, IndicateursMensuelsPolicy.variation(760.0, 3556.6666667, 300.0), 0.01);
    }

    @Test
    void matchesSeptemberReferenceValuesForRahmani() {
        double referenceCa = IndicateursMensuelsPolicy.moyenneReference(List.of(5020.0, 3670.0, 1980.0));
        double referenceCas = IndicateursMensuelsPolicy.moyenneReference(List.of(6.0, 4.0, 2.0));
        double variationCa = IndicateursMensuelsPolicy.variation(760.0, referenceCa, 300.0);
        double variationCas = IndicateursMensuelsPolicy.variation(1.0, referenceCas, 1.0);
        double variationMixte = StatutMensuelPolicy.variationMixte(variationCa, variationCas);
        List<LocalDate> dates = List.of(
            LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 26),
            LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 29),
            LocalDate.of(2026, 9, 8));
        var silence = IndicateursMensuelsPolicy.silence(dates, REFERENCE);

        assertEquals(3556.67, referenceCa, 0.01);
        assertEquals(4.00, referenceCas, 0.01);
        assertEquals(-78.63, variationCa, 0.01);
        assertEquals(-75.00, variationCas, 0.01);
        assertEquals(-77.18, variationMixte, 0.01);
        assertEquals(22, silence.joursSansActivite());
        assertEquals(89.0 / 7, silence.intervalleEffectif(), 0.01);
        assertEquals(34.61, silence.scoreSilence(), 0.01);
    }

    @Test
    void matchesSeptemberReferenceValuesForElGhazouaniAndBoukili() {
        double elCaRef = IndicateursMensuelsPolicy.moyenneReference(List.of(7210.0, 7920.0, 0.0));
        double elCasRef = IndicateursMensuelsPolicy.moyenneReference(List.of(8.0, 9.0, 0.0));
        assertEquals(5043.33, elCaRef, 0.01);
        assertEquals(5.66667, elCasRef, 0.00001);
        assertEquals(-100.0, IndicateursMensuelsPolicy.variation(0.0, elCaRef, 300.0), 0.01);
        assertEquals(-100.0, IndicateursMensuelsPolicy.variation(0.0, elCasRef, 1.0), 0.01);

        double boukiliCaRef = IndicateursMensuelsPolicy.moyenneReference(List.of(11400.0, 10230.0, 6680.0));
        double boukiliCasRef = IndicateursMensuelsPolicy.moyenneReference(List.of(15.0, 14.0, 9.0));
        assertEquals(9436.67, boukiliCaRef, 0.01);
        assertEquals(12.66667, boukiliCasRef, 0.00001);
        assertEquals(39.99, IndicateursMensuelsPolicy.variation(13210.0, boukiliCaRef, 300.0), 0.01);
        assertEquals(26.32, IndicateursMensuelsPolicy.variation(16.0, boukiliCasRef, 1.0), 0.01);
    }

    @Test
    void computesImpactThresholdFromEligibleTopTwentyPercentAndFallbackPercentile() {
        assertEquals(100.0, IndicateursMensuelsPolicy.seuilImpact(
                List.of(100.0, 90.0, 80.0, 70.0, 60.0), List.of(200.0)), 0.0001);
        assertEquals(280.0, IndicateursMensuelsPolicy.seuilImpact(
                List.of(), List.of(100.0, 200.0, 300.0)), 0.0001);
        assertEquals(1.0, IndicateursMensuelsPolicy.seuilImpact(List.of(), List.of()));
    }

    @Test
    void reproducesRahmaniRiskAndFinalUrgencyFromReferenceValues() {
        double baisseReference = 77.18;
        double baisseCourte = 56.97;
        double silence = 34.61;
        double scoreValeur = 69.97;
        double tendance = 0.40 * baisseReference + 0.60 * baisseCourte;
        double risque = ScoreMetierPolicy.scoreRisque(tendance, silence, scoreValeur);
        double urgenceBrute = ScoreMetierPolicy.scoreUrgence(risque, 100.0, 100.0, 0.0);
        double perteCa = 2796.67;
        double seuilImpact = 8181.43;
        double facteur = ScoreMetierPolicy.facteurMaterialite(perteCa, seuilImpact);
        double urgenceFinale = ScoreMetierPolicy.urgenceFinale(urgenceBrute, facteur);

        assertEquals(41.76, risque, 0.01);
        assertEquals(66.70, urgenceBrute, 0.01);
        assertEquals(0.57, facteur, 0.01);
        assertEquals(38.17, urgenceFinale, 0.01);
    }
}
