package com.vactis.service;

import com.vactis.model.Controle.Controle;
import com.vactis.model.Controle.TypeControle;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.service.Activite.SegmentationServiceTestAccess;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaliersControleTest {

    private static Controle palier(TypeControle type, String etat, long min, long max) {
        Controle c = new Controle();
        c.setType(type);
        c.setEtat(etat);
        c.setMinCA(min);
        c.setMaxCA(max);
        c.setActif(true);
        return c;
    }

    @Test
    void ruptureParDefautIdentiqueAuxSeuilsHistoriques() {
        PaliersControle defauts = PaliersControle.defauts(TypeControle.RUPTURE);
        for (double ratio = 0.0; ratio <= 6.0; ratio += 0.001) {
            assertEquals(SilenceAlertPolicy.niveauRupture(ratio), SilenceAlertPolicy.niveauRupture(ratio, defauts),
                    "ratio " + ratio);
        }
        // Valeurs décimales entre deux entiers : pas de trou
        assertEquals(SilenceAlertPolicy.NiveauRupture.AUCUNE, SilenceAlertPolicy.niveauRupture(1.4999, defauts));
        assertEquals(SilenceAlertPolicy.NiveauRupture.SURVEILLANCE, SilenceAlertPolicy.niveauRupture(1.5, defauts));
        assertEquals(SilenceAlertPolicy.NiveauRupture.CRITIQUE, SilenceAlertPolicy.niveauRupture(2000.0, defauts));
    }

    @Test
    void ruptureSuitLesReglesPersonnalisees() {
        PaliersControle paliers = PaliersControle.de(TypeControle.RUPTURE, List.of(
                palier(TypeControle.RUPTURE, "AUCUNE", 0, 120),
                palier(TypeControle.RUPTURE, "SURVEILLANCE", 120, 180),
                palier(TypeControle.RUPTURE, "CRITIQUE", 180, PaliersControle.PLAFOND)));

        assertEquals(SilenceAlertPolicy.NiveauRupture.SURVEILLANCE, SilenceAlertPolicy.niveauRupture(1.3, paliers));
        assertEquals(SilenceAlertPolicy.NiveauRupture.CRITIQUE, SilenceAlertPolicy.niveauRupture(1.8, paliers));
    }

    @Test
    void reglesVidesOuAbsentesRetombentSurLesDefauts() {
        assertEquals("SURVEILLANCE", PaliersControle.de(TypeControle.RUPTURE, List.of()).etatPour(160));
        assertEquals("SURVEILLANCE", PaliersControle.ou(null, TypeControle.RUPTURE).etatPour(160));
    }

    @Test
    void trouEntrePaliersRetombeSurLesDefauts() {
        PaliersControle paliers = PaliersControle.de(TypeControle.URGENCE, List.of(
                palier(TypeControle.URGENCE, "FAIBLE", 0, 20),
                palier(TypeControle.URGENCE, "URGENT", 80, 100)));

        assertEquals("FAIBLE", paliers.etatPour(10));
        assertEquals("MOYEN", paliers.etatPour(30)); // trou -> défaut [25 ; 50[
        assertEquals("URGENT", paliers.etatPour(100));
    }

    @Test
    void urgenceParDefautIdentiqueALAncienCalcul() {
        PaliersControle defauts = PaliersControle.defauts(TypeControle.URGENCE);
        double[] valeurs = {0, 10, 24.9, 25, 30, 49.9, 50, 60, 70, 70.1, 74.9, 75, 90, 99.9, 100};
        for (double urgence : valeurs) {
            for (double risque : valeurs) {
                for (double silence : valeurs) {
                    assertEquals(ancienNiveauUrgence(urgence, risque, silence),
                            SegmentationServiceTestAccess.niveauUrgence(defauts, urgence, risque, silence),
                            "urgence=" + urgence + " risque=" + risque + " silence=" + silence);
                }
            }
        }
    }

    @Test
    void fiabiliteParDefautIdentiqueALAncienCalcul() {
        PaliersControle defauts = PaliersControle.defauts(TypeControle.FIABILITE);
        for (long mois = 0; mois <= 30; mois++) {
            for (boolean anciennete : new boolean[]{true, false}) {
                String attendu = mois >= 3 && anciennete ? "FIABLE" : mois >= 2 ? "PARTIEL" : "NON_FIABLE";
                assertEquals(attendu, SegmentationServiceTestAccess.fiabilite(defauts, mois, anciennete),
                        "mois=" + mois + " anciennete=" + anciennete);
            }
        }
    }

    @Test
    void delaiActionParDefautIdentiqueALAncienCalcul() {
        PaliersControle defauts = PaliersControle.defauts(TypeControle.DELAI_ACTION);
        for (String niveau : new String[]{null, "CRITIQUE", "ELEVEE", "ÉLEVÉE", "NORMALE", "inconnu", "critique"}) {
            for (boolean silence : new boolean[]{true, false}) {
                assertEquals(ActionHorizonPolicy.recommanderHorizon(niveau, silence),
                        ActionHorizonPolicy.recommanderHorizon(niveau, silence, defauts),
                        "niveau=" + niveau + " silence=" + silence);
            }
        }
        assertEquals(15, ActionHorizonPolicy.recommanderHorizon("ELEVEE", false, null));
    }

    @Test
    void delaiActionSuitLesReglesPersonnalisees() {
        Controle critique = new Controle();
        critique.setType(TypeControle.DELAI_ACTION);
        critique.setEtat("CRITIQUE");
        critique.setValeur(5L);
        critique.setActif(true);
        PaliersControle delais = PaliersControle.de(TypeControle.DELAI_ACTION, List.of(critique));

        assertEquals(5, ActionHorizonPolicy.recommanderHorizon("CRITIQUE", false, delais));
        // Niveau non configuré : délai par défaut
        assertEquals(15, ActionHorizonPolicy.recommanderHorizon("ELEVEE", false, delais));
        assertEquals(3, ActionHorizonPolicy.recommanderHorizon("CRITIQUE", true, delais));
    }

    private static RisqueUrgence ancienNiveauUrgence(double urgenceFinale, double scoreRisque, double scoreSilence) {
        if (scoreSilence >= 100.0 || urgenceFinale >= 75.0) return RisqueUrgence.URGENT;
        if (scoreSilence > 70.0 || scoreRisque >= 50.0 || urgenceFinale >= 50.0) return RisqueUrgence.ELEVE;
        if (scoreRisque >= 25.0 || urgenceFinale >= 25.0) return RisqueUrgence.MOYEN;
        return RisqueUrgence.FAIBLE;
    }
}
