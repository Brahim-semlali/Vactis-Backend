package com.vactis.service.Activite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CongeSilencePolicyTest {

    private final LocalDate evaluation = LocalDate.of(2026, 10, 31);
    private final LocalDate derniereActivite = LocalDate.of(2026, 9, 1); // 60 jours avant evaluation

    @Test
    @DisplayName("Médecin sans congé : jours bruts conservés")
    void testMedecinSansConge() {
        int jours = CongeSilencePolicy.calculerJoursSansActiviteAjustes(
                derniereActivite, evaluation, null, null, null);
        assertEquals(60, jours);
    }

    @Test
    @DisplayName("Médecin en congé en cours : jours d'absence exclus, silence gelé")
    void testMedecinEnCongeEnCours() {
        // Congé commencé le 11 octobre (J-20) jusqu'au 10 novembre (J+10)
        LocalDate depart = LocalDate.of(2026, 10, 11);
        LocalDate retour = LocalDate.of(2026, 11, 10);

        int jours = CongeSilencePolicy.calculerJoursSansActiviteAjustes(
                derniereActivite, evaluation, depart, retour, null);

        // Du 1er sept au 11 oct = 40 jours réels d'activité/silence avant le départ en congé
        // Les 20 jours entre le 11 oct et le 31 oct sont exclus
        assertEquals(40, jours);
    }

    @Test
    @DisplayName("Médecin avec congé terminé : seule la période réelle passée en congé est déduite")
    void testMedecinCongeTermine() {
        // Congé du 10 septembre au 25 septembre (15 jours)
        LocalDate depart = LocalDate.of(2026, 9, 10);
        LocalDate retour = LocalDate.of(2026, 9, 25);

        int jours = CongeSilencePolicy.calculerJoursSansActiviteAjustes(
                derniereActivite, evaluation, depart, retour, null);

        // 60 jours bruts - 15 jours de congé = 45 jours
        assertEquals(45, jours);
    }

    @Test
    @DisplayName("Médecin avec retour inconnu mais date de revue : la date de revue sert de borne de suspension")
    void testMedecinRetourInconnuAvecDateRevue() {
        // Congé démarré le 1er octobre, retour inconnu, date de revue au 15 novembre
        LocalDate depart = LocalDate.of(2026, 10, 1);
        LocalDate revue = LocalDate.of(2026, 11, 15);

        int jours = CongeSilencePolicy.calculerJoursSansActiviteAjustes(
                derniereActivite, evaluation, depart, null, revue);

        // Du 1er sept au 1er oct = 30 jours. Du 1er oct au 31 oct = 30 jours de congé exclus
        assertEquals(30, jours);
    }
}
