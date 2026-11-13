package com.vactis.service;

import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RisqueUrgence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoteurCommentaireServiceTest {

    private final MoteurCommentaireService service = new MoteurCommentaireService();

    @Test
    void producesShortDetailedAndDirectorLevelsWithoutCausalClaims() {
        Medecin medecin = new Medecin();
        medecin.setNom("Test");
        medecin.setStatut("RETENTION");
        medecin.setSegment("A");
        medecin.setVariationMixteSur100(-45.0);
        medecin.setRisqueUrgence(RisqueUrgence.URGENT);
        medecin.setScoreValeur(80.0);
        medecin.setScoreRisque(55.0);

        MoteurCommentaireService.CommentaireMoteurDto commentaire = service.genererCommentaire(medecin);
        String resume = service.genererResumeCourt(medecin, commentaire);
        String detail = service.genererCommentaireDetaille(commentaire);
        String direction = service.genererMessageDirection(medecin, commentaire);

        assertTrue(resume.contains("RETENTION") && resume.contains("-45,0%"));
        assertEquals("Risque commercial majeur — Protocole Rétention", commentaire.titreSynthese());
        assertTrue(detail.contains("Situation") || detail.contains("baisse d'activité"));
        assertTrue(detail.contains("Objectif de visite"));
        assertTrue(!detail.contains(commentaire.titreSynthese()));
        assertEquals(commentaire.explicationDetaillee() + " Objectif de visite : " + commentaire.actionConseillee(), detail);
        assertTrue(direction.contains("Priorité URGENT") && direction.contains("ne démontrent pas une causalité"));
    }

    @Test
    void inactivityCommentReportsObservedDataWithoutClaimingRelationshipBreakdown() {
        Medecin medecin = new Medecin();
        medecin.setNom("Test");
        medecin.setStatut("INACTIF");
        medecin.setJoursSansActivite(75);

        String explanation = service.genererCommentaire(medecin).explicationDetaillee();

        assertTrue(explanation.contains("Aucun dossier médical n'a été enregistré"));
        assertTrue(!explanation.contains("Rupture de collaboration constatée"));
    }

    @Test
    void missingRetentionMetricsAreOmittedInsteadOfReportedAsZero() {
        Medecin medecin = new Medecin();
        medecin.setStatut("RETENTION");

        String explanation = service.genererCommentaire(medecin).explicationDetaillee();

        assertTrue(!explanation.contains("0,0%"));
        assertTrue(!explanation.contains("CA courant"));
        assertTrue(!explanation.contains("CA de référence"));
        assertTrue(!explanation.contains("jours sans dépôt"));
    }

    @Test
    void missingInactivityDurationIsNotReportedAsZeroDays() {
        Medecin medecin = new Medecin();
        medecin.setStatut("INACTIF");

        String explanation = service.genererCommentaire(medecin).explicationDetaillee();

        assertTrue(explanation.contains("durée sans activité n'est pas disponible"));
        assertTrue(!explanation.contains("depuis 0 jours"));
    }

    @Test
    void classifiesAllNarrativeScenarios() {
        assertScenario("SILENCE_STRATEGIQUE", medecin("ACTIF", "A", 70, 30.0, 30.0));

        Medecin irregular = medecin("ACTIF", null, null, null, null);
        irregular.setIsProfilIrregulier(true);
        assertScenario("IRREGULIER_SECURISE", irregular);

        assertScenario("A_REACTIVER_MANUEL", medecin("A_REACTIVER", null, null, null, null));
        assertScenario("ONBOARDING_ACTIF", medecin("ONBOARDING", null, null, null, null));
        assertScenario("INACTIF_60_JOURS", medecin("INACTIF", null, 60, null, null));
        assertScenario("INACTIF_SEUIL_A_VERIFIER", medecin("INACTIF", null, 20, null, null));
        assertScenario("RETENTION_CRITIQUE", medecin("RETENTION", null, null, -45.0, null));
        assertScenario("SURVEILLANCE_FLECHISSEMENT", medecin("SURVEILLANCE", null, null, -20.0, null));
        assertScenario("PROGRESSION_FORTE", medecin("PROGRESSION", null, null, 25.0, null));
        assertScenario("DONNEES_INSUFFISANTES", medecin("A_QUALIFIER", null, null, null, null));
        assertScenario("ACTIF_STABLE_REGULIER", medecin("ACTIF", null, null, 0.0, null));
    }

    private void assertScenario(String expectedCode, Medecin medecin) {
        assertEquals(expectedCode, service.genererCommentaire(medecin).codeScenario());
    }

    private Medecin medecin(String statut, String segment, Integer days, Double variation, Double intervalle) {
        Medecin medecin = new Medecin();
        medecin.setStatut(statut);
        medecin.setSegment(segment);
        medecin.setJoursSansActivite(days);
        medecin.setVariationMixteSur100(variation);
        medecin.setIntervalleEffectif(intervalle);
        return medecin;
    }
}