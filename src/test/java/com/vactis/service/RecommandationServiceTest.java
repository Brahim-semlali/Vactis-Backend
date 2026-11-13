package com.vactis.service;

import com.vactis.dto.recommandation.RecommandationsGlobalResponse;
import com.vactis.model.action.Action;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommandationServiceTest {

    @Mock
    private MedecinRepository medecinRepository;

    @Mock
    private ExtractionDonneesRepository extractionDonneesRepository;

    @Mock
    private ActionRepository actionRepository;

    @InjectMocks
    private RecommandationService recommandationService;

    private Medecin medecinDev;
    private Medecin medecinReact;
    private Medecin medecinIrreg;

    @BeforeEach
    void setUp() {
        medecinDev = new Medecin();
        medecinDev.setId(1L);
        medecinDev.setNom("Dupont");
        medecinDev.setSegment("A");
        medecinDev.setStatut("ACTIF_STABLE");
        medecinDev.setNoteInput(4.0);
        medecinDev.setReferenceCa(100.0);
        medecinDev.setCaMensuelMoyen(40.0);

        medecinReact = new Medecin();
        medecinReact.setId(2L);
        medecinReact.setNom("Martin");
        medecinReact.setSegment("C");
        medecinReact.setStatut("INACTIF");
        medecinReact.setRisqueUrgence(RisqueUrgence.ELEVE);

        medecinIrreg = new Medecin();
        medecinIrreg.setId(3L);
        medecinIrreg.setNom("Chirurgien");
        medecinIrreg.setSegment("B");
        medecinIrreg.setStatut("ACTIF_STABLE");
        medecinIrreg.setIsProfilIrregulier(true);
    }

    @Test
    @DisplayName("Tri des prescripteurs dans les 3 listes de recommandations")
    void testGetRecommandations_RepartitCorrectement() {
        when(medecinRepository.findAllWithImportedDossiers()).thenReturn(List.of(medecinDev, medecinReact, medecinIrreg));
        when(extractionDonneesRepository.sumCaByMedecinAndDateRange(any(), any())).thenReturn(List.of());
        when(extractionDonneesRepository.countCasGroupedByMedecin()).thenReturn(List.of(
                new Object[]{1L, 20L},
                new Object[]{2L, 15L},
                new Object[]{3L, 30L}
        ));
        when(extractionDonneesRepository.findDerniereDateReceptionGroupedByMedecin()).thenReturn(List.of());
        when(extractionDonneesRepository.findAllDatesDescending()).thenReturn(List.of(LocalDate.of(2026, 10, 26)));
        when(extractionDonneesRepository.countMoisDistinctsGroupedByMedecin()).thenReturn(
            List.<Object[]>of(new Object[]{3L, 2L}));
        when(extractionDonneesRepository.findPremiereDateReceptionGroupedByMedecin()).thenReturn(
            List.<Object[]>of(new Object[]{3L, LocalDate.of(2026, 1, 15)}));

        RecommandationsGlobalResponse response = recommandationService.getRecommandations();

        assertNotNull(response);
        assertEquals(1, response.getDeveloppement().size());
        assertEquals("Dupont", response.getDeveloppement().get(0).getNomMedecin().replace("Dr Dupont ", "Dupont"));
        assertEquals(0, response.getReactivation().size());
        assertEquals(1, response.getIrreguliersRentables().size());
        assertEquals(2, response.getTotalOpportunites());
        assertEquals(20.0, response.getIrreguliersRentables().get(0).getTauxPresencePct());
    }

        @Test
        @DisplayName("La liste de réactivation conserve les priorités haute et moyenne, pas la faible")
        void filtersInactiveDoctorsByRelativeReactivationPriority() {
        YearMonth referenceMonth = YearMonth.now();
        LocalDate reference = referenceMonth.atEndOfMonth();
        Medecin faible = inactiveDoctor(10L, "Karim", 0.0);
        Medecin moyenne = inactiveDoctor(11L, "Sofia", 50.0);
        Medecin haute = inactiveDoctor(12L, "Nora", 100.0);
        when(medecinRepository.findAllWithImportedDossiers()).thenReturn(List.of(faible, moyenne, haute));
        when(extractionDonneesRepository.sumCaByMedecinAndDateRange(any(), any())).thenReturn(List.of());
        when(extractionDonneesRepository.countCasGroupedByMedecin()).thenReturn(List.of());
        when(extractionDonneesRepository.countMoisDistinctsGroupedByMedecin()).thenReturn(List.of(
            new Object[]{10L, 1L}, new Object[]{11L, 1L}, new Object[]{12L, 1L}));
        when(extractionDonneesRepository.findPremiereDateReceptionGroupedByMedecin()).thenReturn(List.of(
            new Object[]{10L, referenceMonth.atDay(1)},
            new Object[]{11L, referenceMonth.atDay(1)},
            new Object[]{12L, referenceMonth.atDay(1)}));
        when(extractionDonneesRepository.findDerniereDateReceptionGroupedByMedecin()).thenReturn(List.of(
            new Object[]{10L, reference.minusDays(365)},
            new Object[]{11L, reference.minusDays(30)},
            new Object[]{12L, reference}));

        RecommandationsGlobalResponse response = recommandationService.getRecommandations();

        assertEquals(List.of(12L, 11L), response.getReactivation().stream()
            .map(item -> item.getMedecinId()).toList());
        assertEquals("HIGH", response.getReactivation().get(0).getPrioriteReactivation());
        assertEquals("MEDIUM", response.getReactivation().get(1).getPrioriteReactivation());
        assertEquals(30.0, ReactivationPolicy.score(0.0, 0.0, 100.0));
        }

        private Medecin inactiveDoctor(Long id, String name, double valueScore) {
        Medecin doctor = new Medecin();
        doctor.setId(id);
        doctor.setNom(name);
        doctor.setStatut("INACTIF");
        doctor.setScoreValeur(valueScore);
        return doctor;
        }

    @Test
    @DisplayName("Création d'une action à partir d'une recommandation de développement")
    void testCreerActionDepuisRecommandation() {
        when(medecinRepository.findById(1L)).thenReturn(Optional.of(medecinDev));
        when(actionRepository.save(any(Action.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Action action = recommandationService.creerActionDepuisRecommandation(1L, "DEVELOPPEMENT", "commercial1");

        assertNotNull(action);
        assertEquals(30, action.getHorizonJours());
        assertTrue(action.getActionRecommandee().contains("Opportunité de développement"));
        assertEquals("DEVELOPPEMENT", action.getSourceRecommandation());
        assertEquals("ACCEPTEE", action.getDecisionRecommandation());
    }
}
