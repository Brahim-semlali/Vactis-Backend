package com.vactis.service.Activite;

import com.vactis.model.medecin.Medecin;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivitePortefeuilleServiceTest {

    @Mock private ExtractionDonneesRepository extractionRepository;
    @Mock private MedecinRepository medecinRepository;

    @InjectMocks private ActivitePortefeuilleService service;

    private Medecin createMedecinValide() {
        Medecin m = new Medecin();
        m.setId(10L);
        m.setNom("BENJELLOUN");
        m.setPrenom("Karim");
        m.setStatut("ACTIF");
        m.setDatePremiereCollaboration(java.time.LocalDate.now().minusMonths(12));
        m.setTotalCas(15);
        m.setCommentaire("Médecin régulier mais volume fluctuant");
        return m;
    }

    @Test
    void shouldAcceptWhenAll8ConditionsAreMet() {
        Medecin m = createMedecinValide();
        // caMm1=3000, caMm2=5000, caMm3=2000 -> 3 mois actifs, variabilité = (5000-2000)/5000 = 60% >= 30%
        // panierRef = (10000/3) / 6 = 555.5 MAD/cas
        // caCurr=1800, casCurr=3 -> panierCurr = 600 MAD/cas -> 600/555.5 = 1.08 >= 0.65
        boolean resultat = service.verifier8ConditionsActiviteIrreguliere(
                m, 1800L, 3000L, 5000L, 2000L, 3L, 6.0
        );
        assertTrue(resultat, "Toutes les 8 conditions sont réunies : le médecin doit être validé irrégulier");
    }

    @Test
    void shouldRejectWhenCondition1Fails_SeniorityUnder6Months() {
        Medecin m = createMedecinValide();
        m.setDatePremiereCollaboration(java.time.LocalDate.now().minusMonths(2));
        boolean resultat = service.verifier8ConditionsActiviteIrreguliere(
                m, 1800L, 3000L, 5000L, 2000L, 3L, 6.0
        );
        org.junit.jupiter.api.Assertions.assertFalse(resultat, "Condition 1 enfreinte : ancienneté < 6 mois doit rejeter");
    }

    @Test
    void shouldRejectWhenCondition2Fails_LessThan2ActiveMonths() {
        Medecin m = createMedecinValide();
        // 1 seul mois actif parmi M-1, M-2, M-3
        boolean resultat = service.verifier8ConditionsActiviteIrreguliere(
                m, 1800L, 3000L, 0L, 0L, 3L, 6.0
        );
        org.junit.jupiter.api.Assertions.assertFalse(resultat, "Condition 2 enfreinte : moins de 2 mois actifs doit rejeter");
    }

    @Test
    void shouldRejectWhenCondition3Fails_LowVariabilityUnder30Percent() {
        Medecin m = createMedecinValide();
        // Max 4100, Min 4000 -> (4100-4000)/4100 = 2.4% < 30%
        boolean resultat = service.verifier8ConditionsActiviteIrreguliere(
                m, 1800L, 4000L, 4100L, 4050L, 3L, 6.0
        );
        org.junit.jupiter.api.Assertions.assertFalse(resultat, "Condition 3 enfreinte : variabilité < 30% doit rejeter");
    }

    @Test
    void shouldRejectWhenCondition4Fails_TotalCasUnder5() {
        Medecin m = createMedecinValide();
        m.setTotalCas(4); // < 5
        boolean resultat = service.verifier8ConditionsActiviteIrreguliere(
                m, 1800L, 3000L, 5000L, 2000L, 3L, 6.0
        );
        org.junit.jupiter.api.Assertions.assertFalse(resultat, "Condition 4 enfreinte : volume historique < 5 cas doit rejeter");
    }

    @Test
    void shouldRejectWhenCondition5Fails_NoActivityCurrentMonth() {
        Medecin m = createMedecinValide();
        boolean resultat = service.verifier8ConditionsActiviteIrreguliere(
                m, 0L, 3000L, 5000L, 2000L, 0L, 6.0
        );
        org.junit.jupiter.api.Assertions.assertFalse(resultat, "Condition 5 enfreinte : CA courant = 0 doit rejeter");
    }

    @Test
    void shouldRejectWhenCondition6Fails_PanierMoyenCollapsed() {
        Medecin m = createMedecinValide();
        // Panier courant = 500 MAD / 10 cas = 50 MAD/cas vs panierRef = 555 MAD/cas -> ratio < 0.65
        boolean resultat = service.verifier8ConditionsActiviteIrreguliere(
                m, 500L, 3000L, 5000L, 2000L, 10L, 6.0
        );
        org.junit.jupiter.api.Assertions.assertFalse(resultat, "Condition 6 enfreinte : panier moyen effondré doit rejeter");
    }

    @Test
    void shouldRejectWhenCondition7Fails_ActiveReclamation() {
        Medecin m = createMedecinValide();
        m.setCommentaire("Attention: RECLAMATION retard analyse non résolue");
        boolean resultat = service.verifier8ConditionsActiviteIrreguliere(
                m, 1800L, 3000L, 5000L, 2000L, 3L, 6.0
        );
        org.junit.jupiter.api.Assertions.assertFalse(resultat, "Condition 7 enfreinte : présence d'une réclamation doit rejeter");
    }

    @Test
    void shouldRejectWhenCondition8Fails_DoctorInactive() {
        Medecin m = createMedecinValide();
        m.setStatut("INACTIF");
        boolean resultat = service.verifier8ConditionsActiviteIrreguliere(
                m, 1800L, 3000L, 5000L, 2000L, 3L, 6.0
        );
        org.junit.jupiter.api.Assertions.assertFalse(resultat, "Condition 8 enfreinte : statut inactif doit rejeter");
    }
}
